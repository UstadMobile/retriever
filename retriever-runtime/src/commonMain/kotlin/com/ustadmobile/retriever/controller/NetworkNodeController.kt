package com.ustadmobile.retriever.controller

import com.soywiz.klock.DateTime
import com.soywiz.klock.KlockLocale
import com.soywiz.klock.Time
import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.lib.db.entities.NetworkNode


/**
 * Db work
 */
class NetworkNodeController(val context: Any, val db: UmAppDatabase?) {

    suspend fun addNewNode(networkNode: NetworkNode){

        //Check if doesn't already exist. Else update time discovered
        if(db?.networkNodeDao?.findByEndpointUrl(networkNode.networkNodeEndpointUrl?:"").isNullOrEmpty()) {
            networkNode.networkNodeDiscovered = DateTime.nowUnixLong()
            db?.networkNodeDao?.insert(networkNode)
        }else{
            //TODO: Update last discovered?
        }
    }

}
