package com.ustadmobile.retriever.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Network Node - Represents a Node (device) available nearby broadcasting receiver service.
 * As devices(retreiver's service) are discovered, an entry it put in NetworkNode table with its
 * ip address, endpoint url, time of discovery, time lost.
 * The network nodes are removed when they are lost.
 * All network nodes are likely to be purged when the application closes/restarts.
*/
@Entity(indices =
    arrayOf(Index(value = arrayOf("networkNodeEndpointUrl"), name = "networknode_endpoint_index", unique = true)))
@Serializable
open class NetworkNode {

    @PrimaryKey(autoGenerate = true)
    var networkNodeId: Int = 0

    /**
     * E.g http://ipaddr:port/retriever/
     */
    var networkNodeEndpointUrl: String? = null

    var networkNodeDiscovered: Long = 0

    var networkNodeLost: Long = 0

    /**
     * The most recent time that we had a successful response or request of any kind from this node e.g. availability
     * response, download, or ping (incoming or outgoing)
     */
    var lastSuccessTime: Long = 0

    var networkNodeStatus: Int = STATUS_OK

    constructor(endpointUrl: String, discovered: Long){
        networkNodeEndpointUrl = endpointUrl
        networkNodeDiscovered = discovered
    }

    constructor(endpointUrl: String, discovered: Long, id: Int){
        networkNodeEndpointUrl = endpointUrl
        networkNodeDiscovered = discovered
        networkNodeId = id
    }
    constructor()

    companion object {

        const val STATUS_OK = 1

        const val STATUS_STRUCK_OFF = 2

    }



}