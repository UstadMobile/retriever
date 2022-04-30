package com.ustadmobile.retriever.db.entities

class AvailabilityFileWithNumNodes: AvailabilityObserverItem() {

    var numNodes: Int = 0


    fun getFileName(): String{
        return aoiOriginalUrl?:""
    }
}