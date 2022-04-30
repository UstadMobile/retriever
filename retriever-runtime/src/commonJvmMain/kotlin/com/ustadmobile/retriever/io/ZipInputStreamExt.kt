package com.ustadmobile.retriever.io

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.db.entities.DownloadJobItem
import com.ustadmobile.retriever.IntegrityChecksum
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_SUCCESSFUL
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.RetrieverStatusUpdateEvent
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverListener
import com.ustadmobile.retriever.io.MultiDigestOutputStream.Companion.SUPPORTED_DIGEST_INDEX_MAP
import com.ustadmobile.retriever.io.MultiDigestOutputStream.Companion.arrayOfSupportedDigests
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.*
import java.util.zip.CRC32


/**
 * urlToIdMap is used to fire RetrieverProgressEvents. Zips are only used to handle locally downloaded content, so
 * we are assuming bytes extracted count against locally downloaded bytes.
 */
suspend fun ZipInputStream.extractToDir(
    destProvider: (ZipEntry) -> File,
    urlToJobMap: Map<String, DownloadJobItem>,
    integrityChecksums: Array<IntegrityChecksum> = IntegrityChecksum.values(),
    progressInterval: Int = 200,
    progressListener: RetrieverListener? = null,
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
            val crc32 = CRC32()
            val digestMap = integrityChecksums.associate {
                it to MessageDigest.getInstance(it.messageDigestName)
            }
            val messageDigests = digestMap.values.toTypedArray()

            while(zipIn.nextEntry?.also { zipEntry = it } != null) {
                val destFile = destProvider(zipEntry)
                val downloadJobItem = urlToJobMap[zipEntry.name]
                    ?: throw IllegalStateException("exactToDir: Zip Entry with name: ${zipEntry.name} is not in urlToJobMap")
                progressListener?.onRetrieverProgress(RetrieverProgressEvent(downloadJobItem.djiUid, zipEntry.name,
                    0L,0L, 0L, zipEntry.size))
                lastProgressUpdateTime = systemTimeInMillis()

                var entryBytesSoFar = 0L
                val (integrityCheckumType, expectedDigest) =  downloadJobItem.djiIntegrity?.let { parseIntegrity(it)}
                    ?: (null to null)
                val destFileOutput = FileOutputStream(destFile)
                val outputStream = MultiDigestOutputStream(destFileOutput, messageDigests, crc32)

                outputStream.use { fileOut ->
                    while(coroutineContext.isActive && zipIn.read(buffer).also { bytesRead = it } != -1) {
                        fileOut.write(buffer, 0, bytesRead)
                        entryBytesSoFar += bytesRead
                        val timeNow = systemTimeInMillis()
                        if(progressListener != null && timeNow - lastProgressUpdateTime >= progressInterval) {
                            channel.send(RetrieverProgressEvent(downloadJobItem.djiUid, zipEntry.name, entryBytesSoFar,
                                entryBytesSoFar,0L, zipEntry.size))
                            lastProgressUpdateTime = timeNow
                        }
                    }
                    fileOut.flush()

                    val actualDigests = digestMap.map {
                        it.key to it.value.digest()
                    }.toMap()

                    val finalStatus = if(integrityCheckumType != null &&
                            !Arrays.equals(expectedDigest, actualDigests[integrityCheckumType])) {
                        //fail integrity check; discard

                        destFile.delete()
                        STATUS_ATTEMPT_FAILED
                    }else if(entryBytesSoFar == zipEntry.size) {
                        STATUS_SUCCESSFUL
                    }else {
                        STATUS_QUEUED
                    }

                    val checksums = if(finalStatus == STATUS_SUCCESSFUL) {
                        FileChecksums.fromMap(actualDigests, crc32.value)
                    }else {
                        null
                    }

                    channel.send(RetrieverProgressEvent(downloadJobItem.djiUid, zipEntry.name, entryBytesSoFar,
                        entryBytesSoFar,0L, zipEntry.size))
                    progressListener?.onRetrieverStatusUpdate(RetrieverStatusUpdateEvent(downloadJobItem.djiUid,
                        zipEntry.name, finalStatus, checksums))
                }
                messageDigests.forEach {
                    it.reset()
                }
                crc32.reset()
            }
        }catch(e: Exception) {
            throw e
        } finally {
            channel.close()
        }
    }



}
