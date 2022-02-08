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
     * Origin URL for this file
     */
    //@PrimaryKey
    var afOriginUrl: String? = null

    /**
     * Location of the file locally on this node
     */
    var afFilePath: String? = null

    /**
     * The file size.
     */
    var afFileSize: Long = 0

    constructor(){}

    constructor(fileUrl: String, location: String){
        afOriginUrl = fileUrl
        afFilePath = location
    }

    fun getFileName(): String{
        return afOriginUrl?:"-"
        //return afOriginUrl?.substring(0, afOriginUrl?.lastIndexOf("/")?:0).toString()
    }
}