package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Entity representing available files locally in this node.
 */

@Entity
@Serializable
open class AvailableFile {

    @PrimaryKey(autoGenerate = true)
    var availableFileUid: Long = 0

    /**
     * Unique url of the file.
     */
    var availableFileUrl: String? = null

    /**
     * Location of the file locally on this node
     */
    var availableFileLocation: String? = null

    constructor(){}

    constructor(fileUrl: String, location: String){
        availableFileUrl = fileUrl
        availableFileLocation = location
    }
}