package com.ustadmobile.retriever


import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.retriever.db.RetrieverDatabase
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce


class AvailabilityManager(
    val database: RetrieverDatabase,
    private val availabilityChecker: AvailabilityChecker,
    private val strikeOffMaxFailures: Int = 3,
    private val strikeOffTimeWindow: Long = (1000 * 60 * 3),
    private val retryDelay: Long = 1000,
    coroutineScope: CoroutineScope = GlobalScope,
) {

    private val numProcessors: Int = DEFAULT_NUM_PROCESSORS

    /**
     * Sending anything on this channel will result in one queue check. If there is an available
     * processor, one new item will be started.
     *
     * The channel receives a boolean. A value of true indicates that a previous check failed,
     * and we need to run a check if there are any observers where there are no longer cheks
     * pending due to the failure.
     */
    private val checkQueueSignalChannel = Channel<Boolean>(Channel.UNLIMITED)

    @ExperimentalCoroutinesApi
    private val jobItemProducer : ReceiveChannel<AvailabilityCheckJob>

    data class AvailabilityCheckJob(val networkNode: NetworkNode, val fileUrls: List<String>)

    private val availabilityObserverAtomicId = atomic(0)

    private val availabilityObservers = mutableMapOf<Int, AvailabilityObserver>()

    private val checkJobsInProgress = concurrentSafeListOf<AvailabilityCheckJob>()

    init {
        jobItemProducer = coroutineScope.produceJobs()
        coroutineScope.launch {
            repeat(numProcessors) {
                launchProcessor(it, jobItemProducer)
            }

            checkQueueSignalChannel.send(false)
        }
    }

    /**
     * Adds an observer information to the watch list database(AvailabilityObserverItem)
     */
    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver) : Int {
        //Put the availability observer id and its observer
        val listenerUid = availabilityObserverAtomicId.incrementAndGet()
        availabilityObservers[listenerUid] = availabilityObserver

        GlobalScope.launch {
            val initialInfo = database.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                txDb.availabilityObserverItemDao.insertList(
                    availabilityObserver.originUrls.map {
                        AvailabilityObserverItem(it, listenerUid, availabilityObserver.observerMode)
                    }
                )

                txDb.availabilityResponseDao.findAllListenersAndAvailabilityByTime(0, listenerUid,
                    strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow)
            }
            checkQueueSignalChannel.trySend(false)
            fireAvailabilityEvent(initialInfo, 0)
        }

        return listenerUid
    }


    /**
     * Remove observer information from the map and watchlist
     */
    suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver){
        val keyToRemove =
            availabilityObservers.entries.firstOrNull{it.value == availabilityObserver}?.key ?: 0

        database.availabilityObserverItemDao.deleteByListenerUid(keyToRemove)
        availabilityObservers.remove(keyToRemove)
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.produceJobs() = produce<AvailabilityCheckJob> {
        do{
            val hadFailure = checkQueueSignalChannel.receive()
            println("hadFailure? : $hadFailure")
            val availabilityUpdatesToFire = mutableMapOf<Int, List<FileAvailabilityWithListener>>()

            val inProgressEndpoints = checkJobsInProgress.mapNotNull { it.networkNode.networkNodeEndpointUrl }
            //Get all available items(files) that are pending (no response of)
            val pendingItems : List<AvailabilityObserverItemWithNetworkNode> = database
                    .withDoorTransactionAsync(RetrieverDatabase::class
            ) { txDb ->
                /**
                 * If there was a failure, then we need to check if there are any observers where the availability info
                 * can now be considered final. This is important to make sure that we deliver an AvailabilityEvent
                 * where checksPending = false (e.g. so a Downloader knows there is nothing left to wait for etc).
                 */
                if(hadFailure) {
                    val listenerIdsAffectedByFailure = txDb.availabilityObserverItemDao
                        .findObserverIdsAffectedByNodeFailure(strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow)
                    listenerIdsAffectedByFailure.forEach { listenerId ->
                        availabilityUpdatesToFire[listenerId] = txDb.availabilityResponseDao
                            .findAllListenersAndAvailabilityByTime(0, listenerId,
                                strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow)
                    }
                }

                txDb.availabilityObserverItemDao.findPendingItems(
                    strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow
                ).filter {
                    it.networkNode.networkNodeEndpointUrl !in inProgressEndpoints
                }
            }

            availabilityUpdatesToFire.forEach { entry ->
                fireAvailabilityEvent(entry.value, 0)
            }

            println("Retriever: AvailabilityManager :  produceJobs(): " + pendingItems.size + " pendingItems.")

            val grouped = pendingItems.groupBy { it.networkNode.networkNodeId }
            println("Retriever:AvailabilityManager : grouped by networkid: " + grouped.size)

            //Use kotlin to group by networknode uid
            pendingItems.groupBy { it.networkNode.networkNodeId }.forEach {
                val availabilityCheckJob = AvailabilityCheckJob(it.value.first().networkNode,
                    it.value.map{it.aoiOriginalUrl?:""})
                checkJobsInProgress += availabilityCheckJob
                send(availabilityCheckJob)
            }

        }while(isActive)
    }

    /**
     * To emit availability changed events
     */
    private fun CoroutineScope.launchProcessor(
        id: Int,
        channel: ReceiveChannel<AvailabilityCheckJob>
    ) = launch{


        //Runs checkAvailability (that checks availability and populates AvailabilityResponse)
        // for every node id
        for(item in channel){
            var itemFailed = false
            try {
                Napier.d("AvailabilityManager $id: item networkid: " +  item.networkNode.networkNodeId)

                //Returns result<String, Boolean> and networkNodeId
                val availabilityCheckerResult : AvailabilityCheckerResult=
                    availabilityChecker.checkAvailability(item.networkNode, item.fileUrls)

                // Add to AvailabilityResponse table
                val currentTime = systemTimeInMillis()
                val responseMap = availabilityCheckerResult.results.map { it.originUrl to it }.toMap()
                val allResponses = item.fileUrls.map { originUrl ->
                    AvailabilityResponse(item.networkNode.networkNodeId, originUrl,
                        responseMap.containsKey(originUrl), currentTime)
                }

                val affectedResult = database.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                    txDb.availabilityResponseDao.insertList(allResponses)
                    txDb.availabilityResponseDao.findAllListenersAndAvailabilityByTime(currentTime, 0,
                        strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow)
                }

                fireAvailabilityEvent(affectedResult, item.networkNode.networkNodeId)
            }catch(e: Exception) {
                //record a failure for this node
                database.networkNodeFailureDao.insert(NetworkNodeFailure().apply {
                    failNetworkNodeId = item.networkNode.networkNodeId
                    failTime = systemTimeInMillis()
                })
                itemFailed = true
                delay(retryDelay)
            }finally {
                checkJobsInProgress -= item
                checkQueueSignalChannel.send(itemFailed)
            }
        }
    }

    private fun fireAvailabilityEvent(
        availabilityAndListenersList: List<FileAvailabilityWithListener>,
        fromNetworkNodeId: Int
    ) {
        availabilityAndListenersList.groupBy {
            it.listenerUid
        }.forEach { entries ->
            val entriesByUrl = entries.value.groupBy { it.fileUrl }
            val availabilityEventInfoMap = entriesByUrl.map { urlEntry ->
                val firstValue = urlEntry.value.firstOrNull()
                val url = urlEntry.key ?: throw IllegalArgumentException("Null URL on response #${entries.key}")
                url to AvailabilityEventInfo(url, firstValue?.available ?: false,
                    firstValue?.checksPending ?: true,
                    urlEntry.value.mapNotNull { it.networkNodeEndpointUrl } )
            }.toMap()

            val checksPending = entries.value.any { it.checksPending }
            availabilityObservers[entries.key]?.onAvailabilityChanged?.onAvailabilityChanged(
                AvailabilityEvent(availabilityEventInfoMap, fromNetworkNodeId,
                    checksPending))
        }
    }


    internal fun checkQueue() {
        checkQueueSignalChannel.trySend(false)
    }

    /**
     * Handle when a NetworkNode is struck off for having too many recent failures.
     */
    internal suspend fun handleNodeStruckOff(transactionDb: RetrieverDatabase, nodeLostId: Int) {
        val affectedListeners = transactionDb.availabilityResponseDao.findListenersAffectedByNodeStruckOff(nodeLostId)
        affectedListeners.forEach { listenerId ->
            val updatedResponses = transactionDb.availabilityResponseDao.findAllListenersAndAvailabilityByTime(
                0, listenerId, strikeOffMaxFailures,
                systemTimeInMillis() - strikeOffTimeWindow)
            fireAvailabilityEvent(updatedResponses, listenerId)
        }
    }

    fun close() {
        jobItemProducer.cancel()
    }


    companion object{
        const val DEFAULT_NUM_PROCESSORS = 10

    }
}