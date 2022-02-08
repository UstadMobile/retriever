package com.ustadmobile.retriever.controller

import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.NodeListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class NodeListController(context: Any, val db: RetrieverDatabase, val view: NodeListView) {

    fun onCreate(){
        GlobalScope.launch {
            val allNodes = db.networkNodeDao.findAllActiveNodes()
            println("P2PManager: Number of nodes: " + allNodes.size)
        }
        view.nodeList = db.networkNodeDao.findAllActiveNodesLive()
    }


}