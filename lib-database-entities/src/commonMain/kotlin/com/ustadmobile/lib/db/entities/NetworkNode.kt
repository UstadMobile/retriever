package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Network Node - Represents a Node (device) available nearby broadcasting receiver service.
 * As devices(retreiver's service) are discovered, an entry it put in NetworkNode table with its
 * ip address, endpoint url, time of discovery, time lost.
 * The network nodes are removed when they are lost.
 * All network nodes are likely to be purged when the application closes/restarts. TODO: verify
*/
@Entity
@Serializable
open class NetworkNode {

    @PrimaryKey(autoGenerate = true)
    var networkNodeId: Long = 0

    var networkNodeIPAddress: String? = null

    /**
     * E.g http://ipaddr:port/retriever/
     */
    var networkNodeEndpointUrl: String? = null

    var networkNodeDiscovered: Long = 0

    var networkNodeLost: Long = 0

    constructor(ipAddress: String, endpointUrl: String, discovered: Long){
        networkNodeIPAddress = ipAddress
        networkNodeEndpointUrl = endpointUrl
        networkNodeDiscovered = discovered
    }

    constructor(ipAddress: String, endpointUrl: String, discovered: Long, id: Long){
        networkNodeIPAddress = ipAddress
        networkNodeEndpointUrl = endpointUrl
        networkNodeDiscovered = discovered
        networkNodeId = id
    }
    constructor()




}