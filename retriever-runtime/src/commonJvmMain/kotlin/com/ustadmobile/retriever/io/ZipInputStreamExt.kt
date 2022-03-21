package com.ustadmobile.retriever.io

import com.ustadmobile.retriever.ext.totalSize
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_COMPLETE
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_QUEUED
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverProgressListener
import com.ustadmobile.retriever.fetcher.ZipExtractionProgressListener


/**
 * urlToIdMap is used to fire RetrieverProgressEvents
 */
suspend fun ZipInputStream.extractToDir(
    destProvider: (ZipEntry) -> File,
    urlToIdMap: Map<String, Long>,
    progressInterval: Int = 200,
    progressListener: RetrieverProgressListener? = null,
) {
    lateinit var zipEntry: ZipEntry
    val buffer = ByteArray(8192)
    var bytesRead = 0

    var lastProgressUpdateTime = 0L

    while(nextEntry?.also { zipEntry = it } != null) {
        val destFile = destProvider(zipEntry)
        val downloadJobUid = urlToIdMap[zipEntry.name] ?: -1L
        progressListener?.onRetrieverProgress(RetrieverProgressEvent(downloadJobUid, zipEntry.name, 0L,
            zipEntry.size, STATUS_RUNNING))
        lastProgressUpdateTime = systemTimeInMillis()

        var entryBytesSoFar = 0L
        FileOutputStream(destFile).use { fileOut ->
            while(coroutineContext.isActive && this.read(buffer).also { bytesRead = it } != -1) {
                fileOut.write(buffer, 0, bytesRead)
                entryBytesSoFar += bytesRead
                val timeNow = systemTimeInMillis()
                if(progressListener != null && timeNow - lastProgressUpdateTime >= progressInterval) {
                    progressListener.onRetrieverProgress(
                        RetrieverProgressEvent(downloadJobUid, zipEntry.name, entryBytesSoFar, zipEntry.size,
                            STATUS_RUNNING))
                    lastProgressUpdateTime = timeNow
                }
            }
            fileOut.flush()

            val finalStatus = if(entryBytesSoFar == zipEntry.compressedSize) {
                STATUS_COMPLETE
            }else {
                STATUS_QUEUED
            }

            progressListener?.onRetrieverProgress(RetrieverProgressEvent(downloadJobUid, zipEntry.name, entryBytesSoFar,
                zipEntry.size, finalStatus))
        }
    }
}
