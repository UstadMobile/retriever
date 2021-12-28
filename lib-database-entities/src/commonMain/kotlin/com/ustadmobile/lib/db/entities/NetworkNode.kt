package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Network Node
*/
@Entity
@Serializable
open class NetworkNode {

    @PrimaryKey(autoGenerate = true)
    var networkNodeId: Long = 0

    var networkNodeIPAddress: String? = null

    var networkNodeEndpointUrl: String? = null

    var networkNodeDiscovered: Long = 0





}