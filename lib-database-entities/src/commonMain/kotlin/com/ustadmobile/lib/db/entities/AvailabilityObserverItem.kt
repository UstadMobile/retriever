package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Availability Watch List
 * This entity stores the watch list file's request url.
 * The listener uid is the listener Atomic int of the Listener's object
 *
 * As a request is made with a list of files, an availability entry is put too.
*/

@Entity
@Serializable
open class AvailabilityObserverItem {

    @PrimaryKey(autoGenerate = true)
    var aoiId: Int = 0

    var aoiOriginalUrl: String? = null

    var aoiListenerUid: Int = 0

    var aoiResultMode: Int = MODE_SUMMARY_ONLY

    constructor(url: String, listenerUid: Int, resultMode: Int){
        aoiOriginalUrl = url
        aoiListenerUid = listenerUid
        aoiResultMode = resultMode
    }
    constructor(){

    }

    companion object {

        const val MODE_SUMMARY_ONLY = 1

        const val MODE_INC_AVAILABLE_NODES = 2

    }

}