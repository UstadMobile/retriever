package com.ustadmobile.lib.db.entities

import androidx.annotation.NonNull
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable
import kotlin.math.log

/**
 * Availability Response table
 * This table represents the response as per availability of a particular request and node
 * When the request api gets a request for a list of files, it creates a response object (this)
 * that maps every node with a particular origin url. Any update to if the node has that particular
 * file sets availabilityAvailable flag and records the time.
 *
*/
@Entity(indices = arrayOf(Index(value = arrayOf("availabilityOriginUrl", "availabilityNetworkNode"), unique = true)))
@Serializable
open class AvailabilityResponse {

    @PrimaryKey(autoGenerate = true)
    var availabilityResponseUid: Long = 0

    var availabilityNetworkNode: Long = 0

    var availabilityOriginUrl: String? = ""

    var availabilityAvailable: Boolean = false

    var availabilityResponseTimeLogged: Long = 0

    var availabilityResponseTimeUpdated: Long = 0


    constructor()
    constructor(networkNodeUid: Long, originUrl: String, available: Boolean, logged: Long){
        availabilityNetworkNode = networkNodeUid
        availabilityOriginUrl = originUrl
        availabilityAvailable = available
        availabilityResponseTimeLogged = logged

    }
}