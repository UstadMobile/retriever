package com.ustadmobile.retriever.fetcher

import java.io.File
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_COMPLETE
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.ext.copyToAndUpdateProgress
import com.ustadmobile.retriever.io.parseIntegrity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.isActive
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*
import kotlin.coroutines.coroutineContext

actual class OriginServerFetcher(
    private val okHttpClient: OkHttpClient
) {

    actual suspend fun download(
        downloadJobItem: DownloadJobItem,
        retrieverProgressListener: RetrieverProgressListener,
    ) {
        try {
            val url = downloadJobItem.djiOriginUrl
                ?: throw IllegalArgumentException("Null URL on ${downloadJobItem.djiUid}")
            val destFile = downloadJobItem.djiDestPath?.let { File(it) }
                ?: throw IllegalArgumentException("Null destination uri on ${downloadJobItem.djiUid}")

            val (digestName, expectedDigest) =  downloadJobItem.djiIntegrity?.let { parseIntegrity(it) }
                ?: (null to null)
            val messageDigest = digestName?.let { MessageDigest.getInstance(it) }

            val bytesAlreadyDownloaded = destFile.length()

            if(bytesAlreadyDownloaded > 0 && messageDigest != null) {
                FileInputStream(destFile).use { fileIn ->
                    var bytesRead = 0
                    val buffer = ByteArray(8 * 1024)
                    while(coroutineContext.isActive && fileIn.read(buffer).also { bytesRead = it } != -1) {
                        messageDigest.update(buffer, 0, bytesRead)
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

                val totalBytes = response.header("content-length") ?.toLong()
                    ?: throw IllegalStateException("$url does not provide a content-length header.")
                retrieverProgressListener.onRetrieverProgress(
                    RetrieverProgressEvent(downloadJobItem.djiUid, url, bytesAlreadyDownloaded, 0L,
                        bytesAlreadyDownloaded, totalBytes, STATUS_RUNNING)
                )

                val fetchProgressWrapper = if(bytesAlreadyDownloaded > 0L) {
                    RetrieverProgressListener {
                        retrieverProgressListener.onRetrieverProgress(it.copy(
                            bytesSoFar = it.bytesSoFar + bytesAlreadyDownloaded,
                            totalBytes = it.totalBytes + bytesAlreadyDownloaded
                        ))
                    }
                }else {
                    retrieverProgressListener
                }

                val body = response.body ?: throw IllegalStateException("Response to $url has no body!")
                val bytesReadFromHttp = body.byteStream().use { bodyIn ->
                    val fileOut = FileOutputStream(destFile, bytesAlreadyDownloaded != 0L)
                    val destOut = if(messageDigest != null) {
                        DigestOutputStream(fileOut, messageDigest)
                    }else {
                        fileOut
                    }

                    destOut.use { outStream ->
                        bodyIn.copyToAndUpdateProgress(outStream, fetchProgressWrapper, downloadJobItem.djiUid,
                            url, totalBytes)
                    }
                }

                val actualDigest = messageDigest?.digest()
                val finalStatus = if(actualDigest != null && !Arrays.equals(expectedDigest, actualDigest)) {
                    destFile.delete()
                    STATUS_ATTEMPT_FAILED
                }else if(totalBytes == bytesReadFromHttp + bytesAlreadyDownloaded) {
                    STATUS_COMPLETE
                }else {
                    STATUS_QUEUED
                }

                retrieverProgressListener.onRetrieverProgress(
                    RetrieverProgressEvent(downloadJobItem.djiUid, url,
                        bytesReadFromHttp + bytesAlreadyDownloaded, 0L, bytesReadFromHttp,
                        totalBytes, finalStatus))
            }

        }catch(e: Exception) {
            throw e
        }
    }

}