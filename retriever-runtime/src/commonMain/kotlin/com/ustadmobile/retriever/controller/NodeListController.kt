package com.ustadmobile.retriever.controller

import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.view.NodeListView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class NodeListController(context: Any, val db: RetrieverDatabase, val view: NodeListView) {

    fun onCreate(){
        view.nodeList = db.networkNodeDao.findAllActiveNodesLive()
    }


}