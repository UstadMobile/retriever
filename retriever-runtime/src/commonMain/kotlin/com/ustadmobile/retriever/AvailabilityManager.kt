package com.ustadmobile.retriever


import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.AvailabilityObserverItemWithNetworkNode

import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.Volatile
import kotlin.math.min

class AvailabilityManager(val database: RetrieverDatabase) {

    private val numProcessors: Int = DEFAULT_NUM_PROCESSORS
    val maxItemAttempts: Int = DEFAULT_NUM_RETRIES

    /**
     * Sending anything on this channel will result in one queue check. If there is an available
     * processor, one new item will be started.
     */
    private val checkQueueSignalChannel = Channel<Boolean>(Channel.UNLIMITED)
    private val activeNetworkNodeIds = concurrentSafeListOf<Long>()

    private val nextListenerId = AtomicInteger(1)

    private var listenerToUrl: MutableMap<Int, String> = mutableMapOf()


    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver){
        //TODO this

        //Insert into WatchList with the observer uid

        availabilityObserver.originUrls.forEach {
            database.availabilityObserverItemDao.insert(AvailabilityObserverItem(
                it,
                nextListenerId.incrementAndGet()))
            listenerToUrl[nextListenerId.get()] = it
        }



    }

    fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver){

        //TODO this:
    }

    @ExperimentalCoroutinesApi
    @Volatile
    private var jobItemProducer : ReceiveChannel<AvailabilityCheckJob>? = null

    data class AvailabilityCheckJob(val networkNodeUid: Long, val fileUrls: List<String>)

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.produceJobs() = produce<AvailabilityCheckJob> {
        var done: Boolean = false

        do{
            checkQueueSignalChannel.receive()
            val numProcessorsAvailable = numProcessors - activeNetworkNodeIds.size


            //Get all available items(files) that are pending (no response of)
            val pendingItems : List<AvailabilityObserverItemWithNetworkNode> =
                database.availabilityObserverItemDao.findPendingItems()

            //Use kotlin to group by networknode uid
            val itemsByNetworkNodeId = pendingItems.groupBy {
                it.networkNodeId
            }.forEach {
                send(AvailabilityCheckJob(it.key, it.value.map { it.aoiOriginalUrl?:"" } ))
            }

            //TODO: Check how to properly update done
            done = true


        }while(!done)

    }


    /**
     * To emit availability changed events
     */
    private suspend fun CoroutineScope.launchProcessor(
        id: Int,
        channel: ReceiveChannel<AvailabilityCheckJob>
    ){

        //TODO
        for(item in channel){
            //Runs the request to node
            //Creates entries in AvailabilityResponse according to the request to the node
            //Calls the observer method

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
        const val DEFAULT_NUM_RETRIES = 5

    }
}