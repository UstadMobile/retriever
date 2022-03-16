package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class RetrieverCommon(
    protected val db: RetrieverDatabase,
    protected val nsdServiceName: String,
    private val availabilityChecker: AvailabilityChecker,
) : Retriever {

    protected val availabilityManager = AvailabilityManager(db, availabilityChecker)

    fun addNewNode(networkNode: NetworkNode){

        println("Retriever: Adding a new node ..")
        GlobalScope.launch {
            //Check if doesn't already exist. Else update time discovered
            if (db.networkNodeDao.findAllByEndpointUrl(networkNode.networkNodeEndpointUrl ?: "")
                    .isNullOrEmpty()
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
            availabilityManager.checkQueueSignalChannel.trySend(true)
        }
    }

    fun updateNetworkNodeLost(endpointUrl: String){

        //TODO: Should also delete responses from that node
        //TODO: Availability observers should be updated.
        GlobalScope.launch {
            db.networkNodeDao.deleteByEndpointUrl(endpointUrl)
        }
    }

    override suspend fun forceStartJob(){
        availabilityManager.runJob()
    }

    override suspend fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver) {
        availabilityManager.addAvailabilityObserver(availabilityObserver)
    }

    override suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver) {
        availabilityManager.removeAvailabilityObserver(availabilityObserver)
    }

}