package com.ustadmobile.retriever

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNodeAndLastFailInfo
import com.ustadmobile.lib.db.entities.NetworkNodeFailure
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Network Service Discovery on Android can report nodes as lost that are not really lost. It can also fail to report
 * lost nodes when they really are lost!
 *
 * The PingManager helps to smooth this out by maintaining records after nodes have been discovered.
 *
 * Rules:
 *
 *  - Normally a ping is sent to/from each node once in the given pingInterval (default 30 seconds)
 *
 *  - If the most recent failure time on a node is more recent than the last success time, but the node has not yet
 *    hit maxPeerNodeFailuresAllowed, then another ping attempt will be made after pingRetryInterval (default 5 seconds)
 *    to check if the node is really gone.
 *
 *  - If a node has hit maxPeerNodeFailuresAllowed AND has been recorded as lost by network service discovery, then it
 *    will be deleted.
 * @param database the RetrieverDatabase singleton
 * @param pingInterval the default interval for pinging other nodes (every 30 seconds by default)
 * @param pingRetryInterval the interval to ping nodes after it has failed once, but before it has been "struck off"
 *
 *
 */
class PingManager(
    private val database: RetrieverDatabase,
    private val pingInterval: Long,
    private val pingRetryInterval: Long,
    private val maxPeerNodeFailuresAllowed: Int,
    private val peerNodeFailureTimePeriod: Long,
    private val pinger: Pinger,
    private val retrieverCoroutineScope: CoroutineScope,
    private val numProcessors: Int = DEFAULT_NUM_PROCESSORS,
    private val updateCommitInterval: Long = 500,
) {

    private val pingProducer: ReceiveChannel<NetworkNodeAndLastFailInfo>

    private val commitUpdatesJob: Job

    private suspend fun RetrieverDatabase.commitUpdates() {
        networkNodeUpdates.forEach {
            networkNodeDao.updateLastSuccessTime(it.value.networkNodeId, it.value.lastSuccessTime)
        }
        networkNodeFailureDao.insertListAsync(networkNodeFailures)
        networkNodeUpdates.clear()
        networkNodeFailures.clear()
    }

    init {
        pingProducer = retrieverCoroutineScope.producePingJobs()
        commitUpdatesJob = retrieverCoroutineScope.launch {
            while(coroutineContext.isActive) {
                nodeUpdateMutex.withLock {
                    if(networkNodeFailures.isNotEmpty() || networkNodeUpdates.isNotEmpty()){
                        database.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                            txDb.commitUpdates()
                        }
                    }
                }
                delay(updateCommitInterval)
            }
        }

        retrieverCoroutineScope.launch {
            repeat(numProcessors){
                launchProcessor(it, pingProducer)
            }

            checkQueueSignal.send(true)


        }
    }

    private val networkNodeUpdates = concurrentSafeMapOf<Int, NetworkNodeAndLastFailInfo>()

    private val networkNodeFailures = concurrentSafeListOf<NetworkNodeFailure>()

    fun interface Pinger {

        suspend fun ping(endpoint: String)

    }

    private val checkQueueSignal = Channel<Boolean>(Channel.UNLIMITED)

    private val nodesBeingPinged = concurrentSafeListOf<NetworkNodeAndLastFailInfo>()

    private val nodeUpdateMutex = Mutex()

    private fun NetworkNodeAndLastFailInfo.lastHeardFromTime() : Long {
        return max(lastFailTime, lastSuccessTime)
    }

    private fun NetworkNodeAndLastFailInfo.nextPingDueTime(): Long {
        return if(lastFailTime > lastSuccessTime && failCount < maxPeerNodeFailuresAllowed) {
            lastHeardFromTime() + pingRetryInterval
        }else {
            lastHeardFromTime() + pingInterval
        }
    }




    private fun CoroutineScope.producePingJobs() = produce<NetworkNodeAndLastFailInfo> {
        var timeToWait = pingInterval
        do {
            withTimeoutOrNull(timeToWait) {
                checkQueueSignal.receive()
            }
            val timeNow = systemTimeInMillis()
            val inProcessNodeIds = nodesBeingPinged.map { it.networkNodeId }.toSet()
            val nodesAndFailInfo = nodeUpdateMutex.withLock {
                database.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                    txDb.commitUpdates()
                    txDb.networkNodeDao.findNodesWithLastFailInfo(
                        timeNow - peerNodeFailureTimePeriod)
                        .filter { it.networkNodeId !in inProcessNodeIds }
                }
            }

            val nodesToPing = nodesAndFailInfo.filter {
                //Retry pings
                (it.lastFailTime > it.lastSuccessTime && it.failCount < maxPeerNodeFailuresAllowed
                        && it.lastHeardFromTime() < (timeNow - pingRetryInterval)) ||
                //normal pings
                (it.lastHeardFromTime() < (timeNow - pingInterval))
            }
            val numProcessorsAvailable = numProcessors - inProcessNodeIds.size
            val nodePingsToSend = nodesToPing.subList(0, min(nodesToPing.size, numProcessorsAvailable))

            nodePingsToSend.forEach {
                nodesBeingPinged += it
                send(it)
            }

            val nextPingDue: Long = nodesAndFailInfo.minByOrNull { it.nextPingDueTime() }?.nextPingDueTime()
                ?: pingInterval
            timeToWait = max(0L, timeNow - nextPingDue)
        }while(isActive)
    }

    private fun CoroutineScope.launchProcessor(
        id: Int,
        channel: ReceiveChannel<NetworkNodeAndLastFailInfo>
    ) = launch {
        for(item in channel) {
            try {
                pinger.ping(item.networkNodeEndpointUrl ?: throw IllegalArgumentException("Network node endpoint is null!"))
                nodeUpdateMutex.withLock {
                    item.lastSuccessTime = systemTimeInMillis()
                    networkNodeUpdates[item.networkNodeId] = item
                }
            }catch(e: Exception) {
                nodeUpdateMutex.withLock {
                    networkNodeFailures += NetworkNodeFailure().apply {
                        failTime = systemTimeInMillis()
                        failNetworkNodeId = item.networkNodeId
                    }
                }

            }finally {
                nodesBeingPinged -= item
            }
        }
    }

    fun close(){
        commitUpdatesJob.cancel()
        pingProducer.cancel()
    }

    companion object {
        const val DEFAULT_NUM_PROCESSORS = 4
    }

}