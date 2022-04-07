package com.ustadmobile.retriever


import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.receiveThenTryReceiveAllAvailable
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
    private val nodeHandler: RetrieverNodeHandler,
    private val retrieverCoroutineScope: CoroutineScope = GlobalScope,
) {

    private val numProcessors: Int = DEFAULT_NUM_PROCESSORS

    /**
     * Sending anything on this channel will result in one queue check. If there is an available
     * processor, one new item will be started.
     *
     * The channel receives an integer. A non-value integer value indicates that the given networknodeid has been struck
     * off, and we therefor need to check for any observers that have been affected
     */
    private val checkQueueSignalChannel = Channel<Int>(Channel.UNLIMITED)

    @ExperimentalCoroutinesApi
    private val jobItemProducer : ReceiveChannel<AvailabilityCheckJob>

    data class AvailabilityCheckJob(val networkNode: NetworkNode, val fileUrls: List<String>)

    private val availabilityObserverAtomicId = atomic(0)

    private val availabilityObservers = mutableMapOf<Int, AvailabilityObserver>()

    private val checkJobsInProgress = concurrentSafeListOf<AvailabilityCheckJob>()

    init {
        jobItemProducer = retrieverCoroutineScope.produceJobs()
        retrieverCoroutineScope.launch {
            repeat(numProcessors) {
                launchProcessor(it, jobItemProducer)
            }

            checkQueueSignalChannel.send(0)
        }
    }

    /**
     * Adds an observer information to the watch list database(AvailabilityObserverItem)
     */
    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver) : Int {
        //Put the availability observer id and its observer
        val listenerUid = availabilityObserverAtomicId.incrementAndGet()
        availabilityObservers[listenerUid] = availabilityObserver

        retrieverCoroutineScope.launch {
            val initialInfo = database.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                txDb.availabilityObserverItemDao.insertList(
                    availabilityObserver.originUrls.map {
                        AvailabilityObserverItem(it, listenerUid, availabilityObserver.observerMode)
                    }
                )

                txDb.availabilityResponseDao.findAllListenersAndAvailabilityByTime(0, listenerUid,
                    strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow)
            }
            checkQueueSignalChannel.trySend(0)
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
            val struckOffNodeIds = checkQueueSignalChannel.receiveThenTryReceiveAllAvailable().filter { it != 0 }

            Napier.d("AvailabilityManager: produceJobs: struck off ids = : $struckOffNodeIds",
                tag = Retriever.LOGTAG)
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
                if(struckOffNodeIds.isNotEmpty()) {
                    val listenerIdsAffectedByFailure = txDb.availabilityObserverItemDao
                        .findObserverIdsAffectedByNodeFailure(struckOffNodeIds)
                    listenerIdsAffectedByFailure.forEach { listenerId ->
                        availabilityUpdatesToFire[listenerId] = txDb.availabilityResponseDao
                            .findAllListenersAndAvailabilityByTime(0, listenerId,
                                strikeOffMaxFailures, systemTimeInMillis() - strikeOffTimeWindow)
                    }
                }

                txDb.availabilityObserverItemDao.findPendingItemsAsync().filter {
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
                database.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                    nodeHandler.handleNetworkNodeFailures(txDb, listOf(NetworkNodeFailure().apply {
                        failTime = systemTimeInMillis()
                        failNetworkNodeId = item.networkNode.networkNodeId
                    }))
                }

                delay(retryDelay)
            }finally {
                checkJobsInProgress -= item
                checkQueueSignalChannel.send(0)
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
        checkQueueSignalChannel.trySend(0)
    }

    /**
     * Handle when a NetworkNode is struck off for having too many recent failures.
     */
    internal suspend fun handleNodesStruckOff(struckOffNodeIds: List<Int>) {
        struckOffNodeIds.forEach {
            checkQueueSignalChannel.send(it)
        }
    }

    fun close() {
        jobItemProducer.cancel()
    }


    companion object{
        const val DEFAULT_NUM_PROCESSORS = 10

    }
}