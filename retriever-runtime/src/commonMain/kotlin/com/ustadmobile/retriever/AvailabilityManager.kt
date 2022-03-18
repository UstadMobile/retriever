package com.ustadmobile.retriever


import com.ustadmobile.door.ext.concurrentSafeListOf
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
    coroutineScope: CoroutineScope = GlobalScope,
) {

    private val numProcessors: Int = DEFAULT_NUM_PROCESSORS

    /**
     * Sending anything on this channel will result in one queue check. If there is an available
     * processor, one new item will be started.
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

            checkQueueSignalChannel.send(true)
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
            database.availabilityObserverItemDao.insertList(
                availabilityObserver.originUrls.map { AvailabilityObserverItem(it, listenerUid) }
            )

            checkQueueSignalChannel.trySend(true)

        }

        return listenerUid
    }

    /**
     * Remove observer information from the map and watchlist
     */
    suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver){
        val keyToRemove =
            availabilityObservers.entries.firstOrNull{it.value == availabilityObserver}?.key
        // TODO: Delete AvailabilityObserverItem corresponding to the key and map

        availabilityObservers.remove(keyToRemove)
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.produceJobs() = produce<AvailabilityCheckJob> {

        println("Retriever: AvailabilityManager :  produceJobs() called ..")
        do{
            checkQueueSignalChannel.receive()

            val inProgressEndpoints = checkJobsInProgress.mapNotNull { it.networkNode.networkNodeEndpointUrl }
            //Get all available items(files) that are pending (no response of)
            val pendingItems : List<AvailabilityObserverItemWithNetworkNode> =
                database.availabilityObserverItemDao.findPendingItems().filter {
                    it.networkNode.networkNodeEndpointUrl !in inProgressEndpoints
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
    private suspend fun CoroutineScope.launchProcessor(
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
                val allResponses = availabilityCheckerResult.result.map {
                    AvailabilityResponse(item.networkNode.networkNodeId, it.key, it.value, currentTime)
                }

                database.availabilityResponseDao.insertList(allResponses)

                val affectedResult: List<FileAvailabilityWithListener> =
                    database.availabilityResponseDao.findAllListenersAndAvailabilityByTime(currentTime)
                affectedResult.groupBy {
                    it.listenerUid
                }.forEach {
                    val fileAvailabilityResultMap = it.value.map {
                        it.fileUrl to it.available
                    }.toMap()

                    availabilityObservers[it.key]?.onAvailabilityChanged?.onAvailabilityChanged(
                        AvailabilityEvent(fileAvailabilityResultMap, item.networkNode.networkNodeId))
                }
            }finally {
                checkJobsInProgress -= item
                checkQueueSignalChannel.send(true)
            }
        }
    }


    internal fun checkQueue() {
        checkQueueSignalChannel.trySend(true)
    }

    fun close() {
        jobItemProducer.cancel()
    }


    companion object{
        const val DEFAULT_NUM_PROCESSORS = 10

    }
}