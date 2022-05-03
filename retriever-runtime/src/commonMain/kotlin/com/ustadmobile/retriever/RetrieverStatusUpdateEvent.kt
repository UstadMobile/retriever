package com.ustadmobile.retriever

import com.ustadmobile.retriever.io.FileChecksums

/**
 * @param downloadJobItemUid The DownloadJobItemUid as per database
 * @param url the origin url being downloaded
 * @status the status as per Retriever.STATUS_ flags
 * @param checksums if the status is STATUS_SUCCESSFUL, then this should be the checksums as per the config
 * @param exception if the status event is STATUS_FAILURE, then the root cause exception will be provided
 */
data class RetrieverStatusUpdateEvent(
    val downloadJobItemUid: Int,
    val url: String,
    val status: Int,
    val checksums: FileChecksums? = null,
    val exception: Exception? = null,
) {
}