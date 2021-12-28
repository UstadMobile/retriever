package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Network Node Watch List
*/
@Entity
@Serializable
open class NetworkNodeWatchList {

    @PrimaryKey(autoGenerate = true)
    var networkNodeWatchListId: Long = 0

    var networkNodeWatchListOriginalUrl: String? = null

    var networkNodeWatchListListenerUid: Long = 0


}