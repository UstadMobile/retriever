package com.ustadmobile.retriever.controller

import com.soywiz.klock.DateTime
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.lib.db.entities.NetworkNode


/**
 * Db work
 */
class NetworkNodeController(val context: Any, val db: UmAppDatabase?) {

    suspend fun addNewNode(networkNode: NetworkNode){

        //Check if doesn't already exist. Else update time discovered
        if(db?.networkNodeDao?.findAllByEndpointUrl(networkNode.networkNodeEndpointUrl?:"").isNullOrEmpty()) {
            networkNode.networkNodeDiscovered = DateTime.nowUnixLong()
            db?.networkNodeDao?.insert(networkNode)
        }else{
            val netWorkNodeToUpdate: NetworkNode = db?.networkNodeDao?.findByEndpointUrl(networkNode.networkNodeEndpointUrl?:"")
            if(netWorkNodeToUpdate != null){
                netWorkNodeToUpdate.networkNodeDiscovered = DateTime.nowUnixLong()
                db?.networkNodeDao?.update(netWorkNodeToUpdate)
            }
        }
    }

    suspend fun updateNetworkNodeLost(networkNode: NetworkNode){
        //TODO: Handle node lost


    }

}
