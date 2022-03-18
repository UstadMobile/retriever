package com.ustadmobile.retriever.testapp.controller

import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.testapp.view.NodeListView


class NodeListController(context: Any, val db: RetrieverDatabase, val view: NodeListView) {

    fun onCreate(){
        view.nodeList = db.networkNodeDao.findAllActiveNodesLive()
    }


}