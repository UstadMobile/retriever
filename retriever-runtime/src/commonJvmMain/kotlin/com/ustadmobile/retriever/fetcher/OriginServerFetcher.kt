package com.ustadmobile.retriever.fetcher

import java.io.File
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_COMPLETE
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_QUEUED
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.ext.copyToAndUpdateProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException

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

            val bytesAlreadyDownloaded = destFile.length()
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
                    FileOutputStream(destFile, bytesAlreadyDownloaded != 0L).use { fileOut ->
                        bodyIn.copyToAndUpdateProgress(fileOut, fetchProgressWrapper, downloadJobItem.djiUid,
                            url, totalBytes)
                    }
                }

                val finalStatus = if(totalBytes == bytesReadFromHttp + bytesAlreadyDownloaded) {
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