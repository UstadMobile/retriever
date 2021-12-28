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
open class Potato {

    @PrimaryKey(autoGenerate = true)
    var potatoUid: Long = 0


    var potatoName: String? = null


}