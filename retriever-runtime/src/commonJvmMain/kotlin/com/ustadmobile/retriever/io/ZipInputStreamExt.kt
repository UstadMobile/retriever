package com.ustadmobile.retriever.io

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_SUCCESSFUL
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverProgressListener
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.*



/**
 * urlToIdMap is used to fire RetrieverProgressEvents. Zips are only used to handle locally downloaded content, so
 * we are assuming bytes extracted count against locally downloaded bytes.
 */
suspend fun ZipInputStream.extractToDir(
    destProvider: (ZipEntry) -> File,
    urlToJobMap: Map<String, DownloadJobItem>,
    progressInterval: Int = 200,
    progressListener: RetrieverProgressListener? = null,
) {
    lateinit var zipEntry: ZipEntry
    val buffer = ByteArray(8192)
    var bytesRead = 0

    var lastProgressUpdateTime = 0L

    //Make sure that progress events are sent out in order, and avoid I/O waiting for progressevents to be dispatched
    val channel = Channel<RetrieverProgressEvent>()
    val zipIn = this
    coroutineScope {
        async {
            for(item in channel) {
                progressListener?.onRetrieverProgress(item)
            }
        }

        try {
            while(zipIn.nextEntry?.also { zipEntry = it } != null) {
                val destFile = destProvider(zipEntry)
                val downloadJobItem = urlToJobMap[zipEntry.name]
                    ?: throw IllegalStateException("exactToDir: Zip Entry with name: ${zipEntry.name} is not in urlToJobMap")
                progressListener?.onRetrieverProgress(RetrieverProgressEvent(downloadJobItem.djiUid, zipEntry.name, 0L,
                    0L, 0L, zipEntry.size, STATUS_RUNNING))
                lastProgressUpdateTime = systemTimeInMillis()

                var entryBytesSoFar = 0L
                val digests = mutableMapOf<String, MessageDigest>()
                val (digestName, expectedDigest) =  downloadJobItem.djiIntegrity?.let { parseIntegrity(it)}
                    ?: (null to null)
                val digest = digestName?.let {
                    digests.getOrPut(digestName) { MessageDigest.getInstance(digestName) }.also { it.reset() }
                }
                val destFileOutput = FileOutputStream(destFile)
                val outputStream = if(digestName != null && expectedDigest != null) {
                    DigestOutputStream(destFileOutput, digest)
                }else {
                    destFileOutput
                }

                outputStream.use { fileOut ->
                    while(coroutineContext.isActive && zipIn.read(buffer).also { bytesRead = it } != -1) {
                        fileOut.write(buffer, 0, bytesRead)
                        entryBytesSoFar += bytesRead
                        val timeNow = systemTimeInMillis()
                        if(progressListener != null && timeNow - lastProgressUpdateTime >= progressInterval) {
                            channel.send(RetrieverProgressEvent(downloadJobItem.djiUid, zipEntry.name, entryBytesSoFar, entryBytesSoFar,
                                0L, zipEntry.size, STATUS_RUNNING))
                            lastProgressUpdateTime = timeNow
                        }
                    }
                    fileOut.flush()

                    val actualDigest = digest?.digest()
                    val finalStatus = if(actualDigest != null && !Arrays.equals(expectedDigest, actualDigest)) {
                        //fail integrity check; discard

                        destFile.delete()
                        STATUS_ATTEMPT_FAILED
                    }else if(entryBytesSoFar == zipEntry.size) {
                        STATUS_SUCCESSFUL
                    }else {
                        STATUS_QUEUED
                    }

                    channel.send(RetrieverProgressEvent(downloadJobItem.djiUid, zipEntry.name, entryBytesSoFar, entryBytesSoFar,
                        0L, zipEntry.size, finalStatus))
                }
            }
        }catch(e: Exception) {
            throw e
        } finally {
            channel.close()
        }
    }



}
