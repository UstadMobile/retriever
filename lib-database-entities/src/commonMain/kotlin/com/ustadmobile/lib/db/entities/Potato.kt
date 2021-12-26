package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ustadmobile.door.annotation.*
import kotlinx.serialization.Serializable

/**
 * Test entity
*/
@Entity
@Serializable
@SyncableEntity(tableId = 1)
open class Potato {

    @PrimaryKey(autoGenerate = true)
    var potatoUid: Long = 0

    @MasterChangeSeqNum
    var potatoPcsn: Long = 0

    @LocalChangeSeqNum
    var potatoLcsn: Long = 0

    @LastChangedBy
    var potatoLcb: Int = 0

    @LastChangedTime
    var potatoLct: Long = 0

    var potatoName: String? = null


}