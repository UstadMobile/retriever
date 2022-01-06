package com.ustadmobile.retriever

import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.door.ext.concurrentSafeListOf

import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.NetworkNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.withContext
import kotlin.jvm.Volatile
import kotlin.math.min

//TODO: can't call UmAppDatabase
class AvailabilityManager(database: UmAppDatabase) {

    val numProcessors: Int = DEFAULT_NUM_PROCESSORS
    val maxItemAttempts: Int = DEFAULT_NUM_RETRIES

    /**
     * Sending anything on this channel will result in one queue check. If there is an available
     * processor, one new item will be started.
     */
    private val checkQueueSignalChannel = Channel<Boolean>(Channel.UNLIMITED)
    private val activeJobItemIds = concurrentSafeListOf<Long>()


    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver){
        //TODO this



    }

    fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver){

        //TODO this:
    }

    @ExperimentalCoroutinesApi
    @Volatile
    private var jobItemProducer : ReceiveChannel<AvailabilityEvent>? = null


    @ExperimentalCoroutinesApi
    private fun CoroutineScope.findAvailability(availabilityObserver: AvailabilityObserver) =
            produce<AvailabilityEvent> {
        var done: Boolean = false

        do{
            checkQueueSignalChannel.receive()
            val numProcessorsAvailable = numProcessors - activeJobItemIds.size


            val allNodes: List<NetworkNode> = database.networkNodeDao.findAllActiveNodes()

            val numJobsToAdd = min(numProcessorsAvailable, allNodes.size)

            for(i in 0 until numJobsToAdd){
                val nodeUid = allNodes[i].networkNodeId
                activeJobItemIds += nodeUid

                //database.
                //TODO: Find availability response for every node
                //Request node a list and get back a response available/not
                // build AvailabilityEvent object
                val availabilityEvent: AvailabilityEvent = AvailabilityEvent(mapOf(), nodeUid)

                send(availabilityEvent)

            }

            //TODO: Check how to properly update done
            done = activeJobItemIds.size == allNodes.size


        }while(!done)

    }


    /**
     * To emit availability changed events
     */
    private fun CoroutineScope.callAvailabilityEvent(id: Int,
                                         channel: ReceiveChannel<AvailabilityEvent>){

        for(item in channel){

            //Call onAvailabilityChanged
            item.observer.onAvailabilityChanged(item)
        }
    }


    suspend fun runJob(availabilityObserver: AvailabilityObserver): Boolean{
        withContext(Dispatchers.Default) {
            val availability = findAvailability(availabilityObserver).also {
                jobItemProducer = it
            }

            repeat(numProcessors) {
                callAvailabilityEvent(it, availability)
            }

            checkQueueSignalChannel.send(true)
        }

        return true
    }


    companion object{
        const val DEFAULT_NUM_PROCESSORS = 10
        const val DEFAULT_NUM_RETRIES = 5

    }
}