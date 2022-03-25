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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class RetrieverCommon(
    protected val db: RetrieverDatabase,
    protected val nsdServiceName: String,
    private val availabilityChecker: AvailabilityChecker,
    private val originServerFetcher: OriginServerFetcher,
    private val localPeerFetcher: LocalPeerFetcher,
) : Retriever {

    protected val availabilityManager = AvailabilityManager(db, availabilityChecker)

    fun addNewNode(networkNode: NetworkNode){

        println("Retriever: Adding a new node ..")
        GlobalScope.launch {
            //Check if doesn't already exist. Else update time discovered
            if (db.networkNodeDao.findAllByEndpointUrl(networkNode.networkNodeEndpointUrl ?: "")
                    .isEmpty()
            ) {
                networkNode.networkNodeDiscovered = DateTime.nowUnixLong()
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
    }

    fun updateNetworkNodeLost(endpointUrl: String){
        availabilityManager.handleNodeLost(endpointUrl)
    }

    override fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver) {
        GlobalScope.launch {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }
    }

    override fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver) {
        GlobalScope.launch {
            availabilityManager.removeAvailabilityObserver(availabilityObserver)
        }
    }

    override suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: RetrieverListener
    ) {
        val batchId = systemTimeInMillis()

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

        Downloader(batchId, availabilityManager, progressListener, originServerFetcher, localPeerFetcher, db).download()

        addFiles(retrieverRequests.map { LocalFileInfo(it.originUrl, it.destinationFilePath) })
    }
}