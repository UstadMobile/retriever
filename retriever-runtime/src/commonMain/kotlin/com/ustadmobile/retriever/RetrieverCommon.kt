package com.ustadmobile.retriever

import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.receiveThenTryReceiveAllAvailable
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.RetrieverListener
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.min

abstract class RetrieverCommon(
    internal val db: RetrieverDatabase,
    internal val config: RetrieverConfig,
    private val availabilityChecker: AvailabilityChecker,
    private val originServerFetcher: OriginServerFetcher,
    private val localPeerFetcher: LocalPeerFetcher,
    private val availabilityManagerFactory: AvailabilityManagerFactory,
    protected val retrieverCoroutineScope: CoroutineScope = GlobalScope,
) : Retriever , RetrieverNodeHandler {

    protected val availabilityManager : AvailabilityManager by lazy {
        availabilityManagerFactory.makeAvailabilityManager(db, availabilityChecker,
            config.strikeOffMaxFailures, config.strikeOffTimeWindow, retryDelay = 1000,
            nodeHandler = this, retrieverCoroutineScope = retrieverCoroutineScope)
    }

    internal open fun start() {
        availabilityManager.checkQueue()
    }

    private val restoreCheckJobs = concurrentSafeMapOf<Int, Job>()

    private val checkRestoreSignal = Channel<Boolean>(Channel.UNLIMITED)

    private val nodeRestorerJob = retrieverCoroutineScope.launch {
        while(isActive) {
            val timeNow = systemTimeInMillis()
            val nextRestorable = db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                txDb.networkNodeDao.restoreNodes(timeNow - config.strikeOffTimeWindow,
                    config.strikeOffMaxFailures)
                txDb.checkNetworkNodeStatusChanges()

                val countFailuresSince = (timeNow - config.strikeOffTimeWindow)
                val restorableTimes = txDb.networkNodeDao.findNetworkNodeRestorableTimes(
                    countFailuresSince, config.strikeOffMaxFailures, timeNow)
                restorableTimes.filter { it.restorableTime > 0L }.minOfOrNull { it.restorableTime }
                    ?: Long.MAX_VALUE
            }

            val waitTime = min(nextRestorable - timeNow, config.strikeOffTimeWindow)
            withTimeoutOrNull(waitTime) {
                checkRestoreSignal.receiveThenTryReceiveAllAvailable()
            }
        }
    }

    suspend fun handleNodeDiscovered(networkNode: NetworkNode){
        Napier.d("Handle new node discovered")
        networkNode.networkNodeDiscovered = systemTimeInMillis()
        val endpointUrl = networkNode.networkNodeEndpointUrl
            ?: throw IllegalArgumentException("handleNodeDiscovered: NetworkNode endpoint is null")

        val timeNow = systemTimeInMillis()
        var checkQueue = true
        db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
            val existingUid = if(networkNode.networkNodeId != 0) {
                networkNode.networkNodeId
            }else {
                txDb.networkNodeDao.findUidByEndpointUrl(endpointUrl)
            }


            //Not using upsert because we want to be sure that NetworkNode id is preserved in case a node is rediscovered
            // SQLite might delete the conflicting row and issue a new id.
            if(existingUid == 0) {
                networkNode.networkNodeDiscovered = timeNow
                networkNode.lastSuccessTime = timeNow
                txDb.networkNodeDao.insertNodeAsync(networkNode)
                checkQueue = true
            }else {
                handleNetworkNodeSuccessful(txDb, listOf(NetworkNodeSuccess(existingUid, timeNow)))
            }
        }

        if(checkQueue) {
            availabilityManager.checkQueue()
        }

    }

    /**
     * See comments on PingManager for notes about how nodes that reported as lost are handled. Android can give false
     * information here!
     */
    suspend fun handleNetworkNodeLost(endpointUrl: String){
        db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
            val nodeLostId = txDb.networkNodeDao.findNetworkNodeIdByEndpointUrl(endpointUrl)
            txDb.networkNodeDao.updateNetworkNodeLostTime(nodeLostId, systemTimeInMillis())
            txDb.checkForNodesToDelete()
        }
    }

    /**
     * Record the given networknodefailures in the database. Delete the node if it has already been recorded as lost
     * and the node has now
     *
     * Failure must be recorded in the database as they are reported by components. These components rely on the database
     * being updated to ensure that the next queue check etc. behaves as expected.
     */
    override suspend fun handleNetworkNodeFailures(transactionDb: RetrieverDatabase, failures: List<NetworkNodeFailure>){
        if(failures.isEmpty())
            return

        val firstFailTime = failures.minByOrNull { it.failTime }?.failTime ?: 0L
        val timeNow = systemTimeInMillis()
        transactionDb.networkNodeFailureDao.insertListAsync(failures)
        transactionDb.networkNodeDao.strikeOffNodes(timeNow - config.strikeOffTimeWindow,
            config.strikeOffMaxFailures, firstFailTime)
        transactionDb.checkForNodesToDelete()
        transactionDb.checkNetworkNodeStatusChanges()
    }

    /**
     * Check the status changes that have been reported in
     */
    private suspend fun RetrieverDatabase.checkNetworkNodeStatusChanges() {
        val statusChanges = networkNodeStatusChangeDao.findAll()
        networkNodeStatusChangeDao.clear()

        val (struckOffNodes, restoredNodes) = statusChanges.partition { it.scNewStatus == NetworkNode.STATUS_STRUCK_OFF }
        availabilityManager.handleNodesStruckOff(struckOffNodes.map { it.scNetworkNodeId} )
        if(restoredNodes.isNotEmpty())
            availabilityManager.checkQueue()
    }

    /**
     * Log that the given network node has been interacted with successfully (e.g. ping, request fulfilled, etc). This
     * may result in the network node restored if it was previously struck off.
     */
    override suspend fun handleNetworkNodeSuccessful(
        transactionDb: RetrieverDatabase,
        successes: List<NetworkNodeSuccess>
    ) {
        val timeNow = systemTimeInMillis()
        successes.groupBy { it.successNodeId }.forEach {
            transactionDb.networkNodeDao.updateLastSuccessTime(it.key, it.value.maxOf { it.successTime })
        }

        transactionDb.networkNodeDao.restoreNodes(timeNow - config.strikeOffTimeWindow,
            config.strikeOffMaxFailures)
        transactionDb.checkNetworkNodeStatusChanges()
    }

    /**
     * If a node was just reported lost or a node recorded a failure, check if we should delete it as per the policy.
     */
    private suspend fun RetrieverDatabase.checkForNodesToDelete() {
        val nodesToDelete = networkNodeDao.findNetworkNodesToDelete(
            systemTimeInMillis() - config.strikeOffTimeWindow, config.strikeOffMaxFailures)
        nodesToDelete.forEach { nodeLostId ->
            availabilityResponseDao.deleteByNetworkNode(nodeLostId.toLong())
            networkNodeDao.deleteByNetworkNodeId(nodeLostId)
            networkNodeFailureDao.deleteByNetworkNodeId(nodeLostId)
        }
    }


    override fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver) {
        retrieverCoroutineScope.launch {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }
    }

    override fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver) {
        retrieverCoroutineScope.launch {
            availabilityManager.removeAvailabilityObserver(availabilityObserver)
        }
    }

    override suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: RetrieverListener
    ) {
        val batchId = systemTimeInMillis()

        //Map of url to destination file
        val completedFileMap = mutableMapOf<String, String>()

        val listenerWrapper = object: RetrieverListener {
            override suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent) {
                progressListener.onRetrieverProgress(retrieverProgressEvent)
            }

            override suspend fun onRetrieverStatusUpdate(retrieverStatusEvent: RetrieverStatusUpdateEvent) {
                if(retrieverStatusEvent.status == Retriever.STATUS_SUCCESSFUL){
                    completedFileMap[retrieverStatusEvent.url] = retrieverRequests
                        .first { it.originUrl == retrieverStatusEvent.url }.destinationFilePath
                }

                progressListener.onRetrieverStatusUpdate(retrieverStatusEvent)
            }
        }

        db.downloadJobItemDao.insertList(retrieverRequests.mapIndexed { index, request ->
            DownloadJobItem().apply {
                djiBatchId = batchId
                djiStatus = STATUS_QUEUED
                djiOriginUrl = request.originUrl
                djiDestPath = request.destinationFilePath
                djiIndex = index
                djiIntegrity = request.sriIntegrity
            }
        })

        Downloader(batchId, availabilityManager, listenerWrapper, originServerFetcher, localPeerFetcher, db,
            strikeOffMaxFailures = config.strikeOffMaxFailures, strikeOffTimeWindow = config.strikeOffTimeWindow).download()

        addFiles(completedFileMap.map { LocalFileInfo(it.key, it.value) })
    }

    override fun close() {
        availabilityManager.close()
        nodeRestorerJob.cancel()
    }

    companion object {
        internal const val DB_NAME = "retrieverdb"

        internal const val PREFERENCES_KEY_PORT = "port"

        internal const val RETRIEVER_PORT_HEADER = "retriever-port"

    }
}