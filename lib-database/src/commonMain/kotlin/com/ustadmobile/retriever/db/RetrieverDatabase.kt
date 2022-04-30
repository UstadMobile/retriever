package com.ustadmobile.retriever.db

import androidx.room.Database
import com.ustadmobile.door.*
import com.ustadmobile.door.entities.*
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.retriever.db.entities.*
import com.ustadmobile.retriever.db.dao.*

@Database(entities = [

    AvailabilityResponse::class,
    AvailabilityObserverItem::class,
    NetworkNode::class,
    LocallyStoredFile::class,
    DownloadJobItem::class,
    NetworkNodeFailure::class,
    NetworkNodeStatusChange::class,

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

    abstract val networkNodeFailureDao: NetworkNodeFailureDao

    abstract val networkNodeStatusChangeDao: NetworkNodeStatusChangeDao

    //TODO: DO NOT REMOVE THIS COMMENT!
    //#DOORDB_SYNCDAO

    companion object {

        const val TAG_DB = DoorTag.TAG_DB

        const val TAG_REPO = DoorTag.TAG_REPO


    }


}

