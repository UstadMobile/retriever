package com.ustadmobile.retriever


import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.AvailabilityObserverItemWithNetworkNode
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.FileAvailabilityWithListener
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.Volatile

class AvailabilityManager(
    val database: RetrieverDatabase,
    val availabilityChecker: AvailabilityChecker) {

    private val numProcessors: Int = DEFAULT_NUM_PROCESSORS

    /**
     * Sending anything on this channel will result in one queue check. If there is an available
     * processor, one new item will be started.
     */
    private val checkQueueSignalChannel = Channel<Boolean>(Channel.UNLIMITED)
    private val activeNetworkNodeIds = concurrentSafeListOf<Long>()

    @ExperimentalCoroutinesApi
    @Volatile
    private var jobItemProducer : ReceiveChannel<AvailabilityCheckJob>? = null


    data class AvailabilityCheckJob(val networkNodeId: Long, val fileUrls: List<String>)

    private val availabilityObserverAtomicId = AtomicInteger(0)
    private val availabilityObservers = mutableMapOf<Int, AvailabilityObserver>()


    suspend fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver){

        //Put the availability observer id and its observer
        val listenerUid = availabilityObserverAtomicId.incrementAndGet()
        availabilityObservers[listenerUid] = availabilityObserver

        database.availabilityObserverItemDao.insertList(
            availabilityObserver.urls2.map { AvailabilityObserverItem(it, listenerUid) }
        )

    }

    suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver){

        val keyToRemove =
            availabilityObservers.entries.firstOrNull{it.value == availabilityObserver}?.key
        // TODO: Delete AvailabilityObserverItem corresponding to the key and map
        availabilityObservers.remove(keyToRemove)
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.produceJobs() = produce<AvailabilityCheckJob> {

        do{
            checkQueueSignalChannel.receive()
            val numProcessorsAvailable = numProcessors - activeNetworkNodeIds.size

            //Get all available items(files) that are pending (no response of)
            val pendingItems : List<AvailabilityObserverItemWithNetworkNode> =
                database.availabilityObserverItemDao.findPendingItems()

            print("AvailabilityManager: produceJobs(): " + pendingItems.size + " pendingItems.")

            //Use kotlin to group by networknode uid
            pendingItems.groupBy {
                it.networkNodeId
            }.forEach {
                send(AvailabilityCheckJob(it.key, it.value.map { it.aoiOriginalUrl?:"" } ))
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
            println("AvailabilityManager: item networkid: " +  item.networkNodeId)

            //Returns result<String, Boolean> and networkNodeId
            val availabilityCheckerResult : AvailabilityCheckerResult=
                availabilityChecker.checkAvailability(item.networkNodeId, item.fileUrls)

            // Add to AvailabilityResponse table
            val currentTime = systemTimeInMillis()
            val allResponses = availabilityCheckerResult.result.map {
                AvailabilityResponse(item.networkNodeId, it.key, it.value, currentTime)
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
                availabilityObservers[it.key]?.onAvailabilityChanged(
                    AvailabilityEvent(fileAvailabilityResultMap, item.networkNodeId)
                )
            }
        }
    }


    //Auto run externally
    suspend fun runJob(): Boolean{

        withContext(Dispatchers.Default) {
            val availability = produceJobs().also {
                jobItemProducer = it
            }

            repeat(numProcessors) {
                launchProcessor(it, availability)
            }

            checkQueueSignalChannel.send(true)
        }

        return true
    }


    companion object{
        const val DEFAULT_NUM_PROCESSORS = 10

    }
}