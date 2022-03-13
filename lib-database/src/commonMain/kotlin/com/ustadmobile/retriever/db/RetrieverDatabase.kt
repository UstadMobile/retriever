package com.ustadmobile.retriever.db

import androidx.room.Database
import com.ustadmobile.core.db.dao.*
import com.ustadmobile.door.*
import com.ustadmobile.door.entities.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.lib.db.entities.*
import com.ustadmobile.retriever.db.dao.AvailabilityObserverItemDao
import com.ustadmobile.retriever.db.dao.AvailabilityResponseDao
import com.ustadmobile.retriever.db.dao.DownloadJobItemDao
import com.ustadmobile.retriever.db.dao.LocallyStoredFileDao

@Database(entities = [

    AvailabilityResponse::class,
    AvailabilityObserverItem::class,
    NetworkNode::class,
    LocallyStoredFile::class,
    DownloadJobItem::class,

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
abstract class RetrieverDatabase : DoorDatabase() {

    abstract val networkNodeDao : NetworkNodeDao

    abstract val availabilityObserverItemDao: AvailabilityObserverItemDao

    abstract val availabilityResponseDao: AvailabilityResponseDao

    abstract val locallyStoredFileDao: LocallyStoredFileDao

    abstract val downloadJobItemDao: DownloadJobItemDao

    //TODO: DO NOT REMOVE THIS COMMENT!
    //#DOORDB_SYNCDAO

    companion object {

        const val TAG_DB = DoorTag.TAG_DB

        const val TAG_REPO = DoorTag.TAG_REPO


    }


}

