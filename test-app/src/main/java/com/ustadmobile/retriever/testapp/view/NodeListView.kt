package com.ustadmobile.retriever.testapp.view

import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.NetworkNode

interface NodeListView{

    var nodeList: DoorDataSourceFactory<Int, NetworkNode>?

}