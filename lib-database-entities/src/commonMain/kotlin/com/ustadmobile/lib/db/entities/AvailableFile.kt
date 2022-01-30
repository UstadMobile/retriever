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

    /**
     * Origin URL for this file
     */
    @PrimaryKey
    var afOriginUrl: String? = null

    /**
     * Location of the file locally on this node
     */
    var afFilePath: String? = null

    constructor(){}

    constructor(fileUrl: String, location: String){
        afOriginUrl = fileUrl
        afFilePath = location
    }
}