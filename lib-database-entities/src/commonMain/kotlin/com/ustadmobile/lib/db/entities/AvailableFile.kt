package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.math.round

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

    constructor(fileUrl: String, location: String, size: Long){
        afOriginUrl = fileUrl
        afFilePath = location
        afFileSize = size
    }

    fun getFileName(): String{
        return afOriginUrl?:"-"
        //return afOriginUrl?.substring(0, afOriginUrl?.lastIndexOf("/")?:0).toString()
    }

    fun getFileSize(): String{
        return formatFileSize(afFileSize)
    }

    fun formatFileSize(fileSize: Long): String {

        val unit: String
        val factor: Long
        if (fileSize > UNIT_GB) {
            factor = UNIT_GB
            unit = "GB"
        } else if (fileSize > UNIT_MB) {
            factor = UNIT_MB
            unit = "MB"
        } else if (fileSize > UNIT_KB) {
            factor = UNIT_KB
            unit = "kB"
        } else {
            factor = 1
            unit = "bytes"
        }

        var unitSize = fileSize.toDouble() / factor.toDouble()
        unitSize = round(unitSize * 100) / 100.0
        return "$unitSize $unit"
    }

    companion object{
        private const val UNIT_GB = 1024L * 1024L * 1024L

        private const val UNIT_MB = 1024L * 1024L

        private const val UNIT_KB: Long = 1024
    }
}