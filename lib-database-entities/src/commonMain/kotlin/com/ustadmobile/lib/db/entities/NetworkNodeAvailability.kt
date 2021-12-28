package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Availability Network Node
*/
@Entity
@Serializable
open class NetworkNodeAvailability {

    @PrimaryKey(autoGenerate = true)
    var availabilityUid: Long = 0

    var availabilityNetworkNode: Long = 0

    var availabilityOriginUrl: String? = null

    var availabilityAvailable: Boolean? = null

    var availabilityTime: Long = 0




}