package com.ustadmobile.retriever.controller

import com.soywiz.klock.DateTime
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.lib.db.entities.NetworkNode


/**
 * Db work
 */
class RetrieverController(val context: Any, val db: RetrieverDatabase?) {

    suspend fun addNewNode(networkNode: NetworkNode){

        //Check if doesn't already exist. Else update time discovered
        if(db?.networkNodeDao?.findAllByEndpointUrl(networkNode.networkNodeEndpointUrl?:"").isNullOrEmpty()) {
            networkNode.networkNodeDiscovered = DateTime.nowUnixLong()
            db?.networkNodeDao?.insert(networkNode)
        }else{
            val netWorkNodeToUpdate: NetworkNode? =
                db?.networkNodeDao?.findByEndpointUrl(
                    networkNode.networkNodeEndpointUrl?:"")
            if(netWorkNodeToUpdate != null){
                netWorkNodeToUpdate.networkNodeDiscovered = DateTime.nowUnixLong()
                netWorkNodeToUpdate.networkNodeLost = 0
                db?.networkNodeDao?.update(netWorkNodeToUpdate)
            }
        }
    }

    suspend fun updateNetworkNodeLost(endpointUrl: String){

        //Check if exists, if does, remove it/ update lost?
        if(!db?.networkNodeDao?.findAllByEndpointUrl(endpointUrl).isNullOrEmpty()){
            val nodeToDelete: NetworkNode? = db?.networkNodeDao?.findByEndpointUrl(endpointUrl)
            if(nodeToDelete != null){
                nodeToDelete.networkNodeLost = DateTime.nowUnixLong()
                db?.networkNodeDao?.update(nodeToDelete)
            }
        }

    }

}
