package com.ustadmobile.retriever

import kotlinx.serialization.Serializable

@Serializable
class PingResponse {

    /**
     * When a server answers a ping, it will provide its own listening port.
     */
    var listeningPort: Int = 0

}