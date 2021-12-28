package com.ustadmobile.retriever.controller

import com.ustadmobile.core.db.UmAppDatabase
import com.ustadmobile.lib.db.entities.NetworkNode


/**
 * Db work
 */
class NetworkNodeController(val context: Any, val db: UmAppDatabase?) {

    fun addNewNode(networkNode: NetworkNode){
        db?.networkNodeDao?.insert(networkNode)
    }

}
