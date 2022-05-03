package com.ustadmobile.retriever.fetcher

import java.io.File
import com.ustadmobile.retriever.db.entities.DownloadJobItem
import com.ustadmobile.retriever.IntegrityChecksum
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.Retriever.Companion.STATUS_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_SUCCESSFUL
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.RetrieverStatusUpdateEvent
import com.ustadmobile.retriever.ext.copyToAndUpdateProgress
import com.ustadmobile.retriever.io.FileChecksums
import com.ustadmobile.retriever.io.MultiDigestOutputStream
import com.ustadmobile.retriever.io.parseIntegrity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.isActive
import java.security.MessageDigest
import java.util.*
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext
import io.github.aakira.napier.Napier
import java.util.concurrent.atomic.AtomicInteger

actual class OriginServerFetcher(
    private val okHttpClient: OkHttpClient,
    private val integrityChecksums: Array<IntegrityChecksum> = IntegrityChecksum.values(),
) {

    val requestIdAtomic = AtomicInteger()

    actual suspend fun download(
        downloadJobItems: List<DownloadJobItem>,
        retrieverListener: RetrieverListener,
    ) {
        val requestId = requestIdAtomic.getAndIncrement()

        val logPrefix = "[OriginServerFetcher: #$requestId]"
        val crc32 = CRC32()
        val digestMap = integrityChecksums.associate {
            it to MessageDigest.getInstance(it.messageDigestName)
        }
        val messageDigests = digestMap.values.toTypedArray()

        downloadJobItems.forEach { downloadJobItem ->
            Napier.d("$logPrefix download: ${downloadJobItem.djiOriginUrl}", tag = Retriever.LOGTAG)
            val url = downloadJobItem.djiOriginUrl
                ?: throw IllegalArgumentException("Null URL on ${downloadJobItem.djiUid}")
            val destFile = downloadJobItem.djiDestPath?.let { File(it) }
                ?: throw IllegalArgumentException("Null destination uri on ${downloadJobItem.djiUid}")
            val bytesAlreadyDownloaded = destFile.length()
            var totalBytes = 0L

            try {
                val (integrityChecksum, expectedDigest) =  downloadJobItem.djiIntegrity?.let { parseIntegrity(it) }
                    ?: (null to null)

                if(bytesAlreadyDownloaded > 0) {
                    Napier.d("$logPrefix ${downloadJobItem.djiOriginUrl} already downloaded $bytesAlreadyDownloaded",
                        tag = Retriever.LOGTAG)
                    FileInputStream(destFile).use { fileIn ->
                        var bytesRead = 0
                        val buffer = ByteArray(8 * 1024)
                        while(coroutineContext.isActive && fileIn.read(buffer).also { bytesRead = it } != -1) {
                            messageDigests.forEach {
                                it.update(buffer, 0, bytesRead)
                            }
                            crc32.update(buffer, 0, bytesRead)
                        }
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if(bytesAlreadyDownloaded != 0L) {
                            addHeader("range", "bytes=$bytesAlreadyDownloaded-")
                        }
                    }
                    .build()


                //TODO: wrap this and run it asynchronously instead
                okHttpClient.newCall(request).execute().use { response ->
                    if(bytesAlreadyDownloaded > 0L && response.code != 206) {
                        throw IOException("$url response code was ${response.code} : expected 206 partial content response")
                    }else if(bytesAlreadyDownloaded == 0L && response.code != 200) {
                        throw IOException("$url response code was ${response.code} : expected 200 OK response")
                    }

                    totalBytes = response.header("content-length") ?.toLong()
                        ?: throw IllegalStateException("$url does not provide a content-length header.")
                    totalBytes += bytesAlreadyDownloaded

                    retrieverListener.onRetrieverProgress(
                        RetrieverProgressEvent(downloadJobItem.djiUid, url, bytesAlreadyDownloaded, 0L,
                            bytesAlreadyDownloaded, totalBytes)
                    )

                    val fetchProgressWrapper = if(bytesAlreadyDownloaded > 0L) {
                        object : RetrieverListener {
                            override suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent) {
                                retrieverListener.onRetrieverProgress(retrieverProgressEvent.copy(
                                    bytesSoFar = retrieverProgressEvent.bytesSoFar + bytesAlreadyDownloaded,
                                    totalBytes = retrieverProgressEvent.totalBytes + bytesAlreadyDownloaded
                                ))
                            }

                            override suspend fun onRetrieverStatusUpdate(retrieverStatusEvent: RetrieverStatusUpdateEvent) {
                                retrieverListener.onRetrieverStatusUpdate(retrieverStatusEvent)
                            }
                        }
                    }else {
                        retrieverListener
                    }

                    val body = response.body ?: throw IllegalStateException("Response to $url has no body!")
                    val bytesReadFromHttp = body.byteStream().use { bodyIn ->
                        val fileOut = FileOutputStream(destFile, bytesAlreadyDownloaded != 0L)
                        val destOut = MultiDigestOutputStream(fileOut, messageDigests, crc32)

                        destOut.use { outStream ->
                            bodyIn.copyToAndUpdateProgress(outStream, fetchProgressWrapper, downloadJobItem.djiUid,
                                url, totalBytes)
                        }
                    }


                    val actualDigests = digestMap.map {
                        it.key to it.value.digest()
                    }.toMap()
                    val finalStatus = if(integrityChecksum != null && !Arrays.equals(expectedDigest,
                            actualDigests[integrityChecksum])) {
                        Napier.e("$logPrefix ${downloadJobItem.djiOriginUrl} - checksum does not match integrity!",
                            tag = Retriever.LOGTAG)
                        //Integrity check failed - discard
                        destFile.delete()
                        STATUS_FAILED
                    }else if(totalBytes == bytesReadFromHttp + bytesAlreadyDownloaded) {
                        Napier.e("$logPrefix ${downloadJobItem.djiOriginUrl} - download completed OK",
                            tag = Retriever.LOGTAG)
                        STATUS_SUCCESSFUL
                    }else {
                        Napier.w("$logPrefix ${downloadJobItem.djiOriginUrl} - download has not received all bytes " +
                                "expected", tag = Retriever.LOGTAG)
                        STATUS_QUEUED
                    }

                    val checksums = if(finalStatus == STATUS_SUCCESSFUL) {
                        FileChecksums.fromMap(actualDigests, crc32.value)
                    }else {
                        null
                    }

                    retrieverListener.onRetrieverProgress(
                        RetrieverProgressEvent(downloadJobItem.djiUid, url,
                            bytesReadFromHttp + bytesAlreadyDownloaded, 0L, bytesReadFromHttp,
                            totalBytes))
                    retrieverListener.onRetrieverStatusUpdate(
                        RetrieverStatusUpdateEvent(downloadJobItem.djiUid, url, finalStatus, checksums))

                    crc32.reset()
                }

            }catch(e: Exception) {
                //Don't just throw an exception, because we could be handling multiple downloads (hence should continue
                // with the next ones)
                Napier.e("$logPrefix Exception attempting to download ${downloadJobItem.djiOriginUrl}", e,
                    tag = Retriever.LOGTAG)
                retrieverListener.onRetrieverStatusUpdate(
                    RetrieverStatusUpdateEvent(downloadJobItem.djiUid, url, STATUS_FAILED, exception = e))
            }
        }
    }

}