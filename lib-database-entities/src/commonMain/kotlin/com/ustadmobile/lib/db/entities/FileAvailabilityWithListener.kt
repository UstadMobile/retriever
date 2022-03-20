package com.ustadmobile.lib.db.entities

class FileAvailabilityWithListener(){
    var listenerUid: Int = 0

    var fileUrl: String? = ""

    var available: Boolean = false

    /**
     * If true, we are waiting for other nodes to answer this. If false, we have up to date availability information
     * from all nodes
     */
    var checksPending: Boolean = false

    var networkNodeEndpointUrl: String? = null
}