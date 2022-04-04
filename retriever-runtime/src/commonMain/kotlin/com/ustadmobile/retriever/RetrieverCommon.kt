package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.RetrieverListener
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class RetrieverCommon(
    internal val db: RetrieverDatabase,
    protected val nsdServiceName: String,
    private val availabilityChecker: AvailabilityChecker,
    private val originServerFetcher: OriginServerFetcher,
    private val localPeerFetcher: LocalPeerFetcher,
    protected val retrieverCoroutineScope: CoroutineScope = GlobalScope,
) : Retriever {

    protected val availabilityManager = AvailabilityManager(db, availabilityChecker)

    suspend fun addNewNode(networkNode: NetworkNode){
        println("Retriever: Adding a new node ..")
        //Check if doesn't already exist. Else update time discovered
        if (db.networkNodeDao.findAllByEndpointUrl(networkNode.networkNodeEndpointUrl ?: "")
                .isEmpty()
        ) {
            networkNode.networkNodeDiscovered = systemTimeInMillis()
            db.networkNodeDao.insert(networkNode)
        } else {
            val netWorkNodeToUpdate: NetworkNode? =
                db.networkNodeDao.findByEndpointUrl(
                    networkNode.networkNodeEndpointUrl ?: ""
                )
            if (netWorkNodeToUpdate != null) {
                netWorkNodeToUpdate.networkNodeDiscovered = DateTime.nowUnixLong()
                netWorkNodeToUpdate.networkNodeLost = 0
                db.networkNodeDao.update(netWorkNodeToUpdate)
            }
        }

        println("Retriever: Sending Signal ..")
        availabilityManager.checkQueue()
    }

    fun updateNetworkNodeLost(endpointUrl: String){
        availabilityManager.handleNodeLost(endpointUrl)
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

        Downloader(batchId, availabilityManager, listenerWrapper, originServerFetcher, localPeerFetcher, db).download()

        addFiles(completedFileMap.map { LocalFileInfo(it.key, it.value) })
    }

    override fun close() {
        availabilityManager.close()
    }

    companion object {
        internal const val DB_NAME = "retrieverdb"
    }
}