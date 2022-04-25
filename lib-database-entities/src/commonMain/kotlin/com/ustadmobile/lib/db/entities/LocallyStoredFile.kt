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
open class LocallyStoredFile() {

    @PrimaryKey(autoGenerate = true)
    var locallyStoredFileUid: Int = 0

    /**
     * Origin URL for this file
     */
    var lsfOriginUrl: String? = null

    /**
     * Location of the file locally on this node. This should be the ordinary file path as per
     * File.absolutePath
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

    /**
     * Base64 encoded SHA-256 checksum
     */
    var lsfSha256: String? = null

    /**
     * Base64 encoded SHA-384 checksum
     */
    var lsfSha384: String? = null

    /**
     * Base64 encoded SHA-512 checksum
     */
    var lsfSha512: String? = null

    constructor(
        originUrl: String,
        filePath: String,
        size: Long,
        crc32: Long,
        sha256: String?,
        sha384: String?,
        sha512: String?
    ) : this() {
        lsfOriginUrl = originUrl
        lsfFilePath = filePath
        lsfFileSize = size
        lsfCrc32 = crc32
        lsfSha256 = sha256
        lsfSha384 = sha384
        lsfSha512 = sha512
    }

}