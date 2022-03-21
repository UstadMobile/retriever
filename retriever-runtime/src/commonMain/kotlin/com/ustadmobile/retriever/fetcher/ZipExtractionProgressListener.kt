package com.ustadmobile.retriever.fetcher

import com.ustadmobile.retriever.util.ZipEntryKmp


fun interface ZipExtractionProgressListener {
    suspend fun onProgress(entry: ZipEntryKmp, bytesSoFar: Long, totalBytes: Long)
}
