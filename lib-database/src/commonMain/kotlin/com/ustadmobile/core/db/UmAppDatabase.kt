package com.ustadmobile.core.db

import androidx.room.Database
import com.ustadmobile.core.db.dao.*
import com.ustadmobile.door.*
import com.ustadmobile.door.entities.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.lib.db.entities.*
import kotlin.js.JsName

@Database(entities = [

    AvailabilityResponse::class,
    AvailabilityWatchList::class,
    NetworkNode::class,

    SyncNode::class,
    //Door Helper entities
    SqliteChangeSeqNums::class,
    UpdateNotification::class,
    ChangeLog::class,
    ZombieAttachmentData::class,
    DoorNode::class

    //TODO: DO NOT REMOVE THIS COMMENT!
    //#DOORDB_TRACKER_ENTITIES

], version = 1)
abstract class UmAppDatabase : DoorDatabase() {



    /**
     * Preload a few entities where we have fixed UIDs for fixed items (e.g. Xapi Verbs)
     */
    fun preload() {

    }

    @JsName("networkNodeDao")
    abstract val networkNodeDao : NetworkNodeDao

    @JsName("syncNodeDao")
    abstract val syncNodeDao: SyncNodeDao

    abstract val syncresultDao: SyncResultDao

    //TODO: DO NOT REMOVE THIS COMMENT!
    //#DOORDB_SYNCDAO

    companion object {

        const val TAG_DB = DoorTag.TAG_DB

        const val TAG_REPO = DoorTag.TAG_REPO


    }


}

