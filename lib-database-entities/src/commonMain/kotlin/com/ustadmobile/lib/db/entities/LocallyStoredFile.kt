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
open class LocallyStoredFile {

    @PrimaryKey(autoGenerate = true)
    var locallyStoredFileUid: Long = 0

    /**
     * Origin URL for this file
     */
    //@PrimaryKey
    var lsfOriginUrl: String? = null

    /**
     * Location of the file locally on this node
     */
    var lsfFilePath: String? = null

    /**
     * The file size.
     */
    var lsfFileSize: Long = 0

    /**
     * The CRC32 of the file (used when serving over zip)
     */
    var lsfCrc32: Long = 0

    constructor(){}

    constructor(originUrl: String, filePath: String){
        lsfOriginUrl = originUrl
        lsfFilePath = filePath
    }

    constructor(originUrl: String, filePath: String, size: Long){
        lsfOriginUrl = originUrl
        lsfFilePath = filePath
        lsfFileSize = size
    }

    fun getFileName(): String{
        return lsfOriginUrl?:"-"
        //return afOriginUrl?.substring(0, afOriginUrl?.lastIndexOf("/")?:0).toString()
    }

    fun getFileSize(): String{
        return formatFileSize(lsfFileSize)
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