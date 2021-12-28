package com.ustadmobile.core.db

import androidx.room.Database
import com.ustadmobile.core.db.dao.*
import com.ustadmobile.door.*
import com.ustadmobile.door.annotation.MinSyncVersion
import com.ustadmobile.door.entities.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.migration.*
import com.ustadmobile.door.util.DoorSqlGenerator
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.*
import kotlin.js.JsName
import kotlin.jvm.JvmField

@Database(entities = [Potato::class,

    NetworkNodeAvailability::class,
    NetworkNodeWatchList::class,
    NetworkNode::class,

    SyncNode::class, SyncResult::class,

    //Door Helper entities
    SqliteChangeSeqNums::class,
    UpdateNotification::class,
    TableSyncStatus::class,
    ChangeLog::class,
    ZombieAttachmentData::class,
    DoorNode::class

    //TODO: DO NOT REMOVE THIS COMMENT!
    //#DOORDB_TRACKER_ENTITIES

], version = 1)
@MinSyncVersion(1)
abstract class UmAppDatabase : DoorDatabase(), SyncableDoorDatabase {




    override val master: Boolean
        get() = false


    /**
     * Preload a few entities where we have fixed UIDs for fixed items (e.g. Xapi Verbs)
     */
    fun preload() {

    }

    @JsName("potatoDao")
    abstract val potatoDao: PotatoDao

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

