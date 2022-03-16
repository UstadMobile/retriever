package com.ustadmobile.retriever.io

import com.ustadmobile.retriever.ext.totalSize
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.fetcher.ZipExtractionProgressListener


suspend fun ZipInputStream.extractToDir(
    destProvider: (ZipEntry) -> File,
    progressInterval: Int = 200,
    progressListener: ZipExtractionProgressListener? = null,
) {
    lateinit var zipEntry: ZipEntry
    val buffer = ByteArray(8192)
    var bytesRead = 0

    var lastProgressUpdateTime = 0L

    while(nextEntry?.also { zipEntry = it } != null) {
        val destFile = destProvider(zipEntry)
        progressListener?.onProgress(zipEntry, 0, zipEntry.totalSize)
        lastProgressUpdateTime = systemTimeInMillis()

        var entryBytesSoFar = 0L
        FileOutputStream(destFile).use { fileOut ->
            while(coroutineContext.isActive && this.read(buffer).also { bytesRead = it } != -1) {
                fileOut.write(buffer, 0, bytesRead)
                entryBytesSoFar += bytesRead
                val timeNow = systemTimeInMillis()
                if(timeNow - lastProgressUpdateTime >= progressInterval) {
                    progressListener?.onProgress(zipEntry, entryBytesSoFar, zipEntry.size)
                    lastProgressUpdateTime = timeNow
                }
            }
            fileOut.flush()

            progressListener?.onProgress(zipEntry, entryBytesSoFar, zipEntry.size)
        }
    }
}
