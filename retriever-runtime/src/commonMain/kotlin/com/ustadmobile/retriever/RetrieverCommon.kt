package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.MultiItemFetcher
import com.ustadmobile.retriever.fetcher.SingleItemFetcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class RetrieverCommon(
    protected val db: RetrieverDatabase,
    protected val nsdServiceName: String,
    private val availabilityChecker: AvailabilityChecker,
    private val singleItemFetcher: SingleItemFetcher,
    private val multiItemFetcher: MultiItemFetcher,
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

        //TODO: Should also delete responses from that node
        //TODO: Availability observers should be updated.
        GlobalScope.launch {
            db.networkNodeDao.deleteByEndpointUrl(endpointUrl)
        }
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

}