package com.ustadmobile.retriever.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
open class DownloadJobItem {

    @PrimaryKey(autoGenerate = true)
    var djiUid: Int = 0

    /**
     * Just the timestamp - we do not expect to launch more than one batch per millisecond
     */
    var djiBatchId: Long = 0

    /**
     * The origin url that we are attempting to download
     */
    var djiOriginUrl: String? = null

    var djiIntegrity: String? = null

    /**
     * The destination path (native file system path) e.g. as per File.absolutePath
     */
    var djiDestPath: String? = null

    var djiStatus = 0

    var djiBytesSoFar = 0L

    var djiTotalSize = 0L

    /**
     * The number of bytes downloaded from local peers so far
     */
    var djiLocalBytesSoFar = 0L

    /**
     * The number of bytes downloaded direct from the origin server itself so far
     */
    var djiOriginBytesSoFar = 0L

    var djiAttemptCount = 0

    /**
     * An index order. We need to ensure that when a download is re-attempted, the item partially downloaded comes first.
     *
     * Might need to do that manually...
     *
     */
    var djiIndex = 0

    companion object {



    }

}