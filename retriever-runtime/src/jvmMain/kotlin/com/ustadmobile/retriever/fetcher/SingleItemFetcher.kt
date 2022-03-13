package com.ustadmobile.retriever.fetcher

import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.toFile
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.ext.copyToAndUpdateProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException

actual class SingleItemFetcher(
    private val okHttpClient: OkHttpClient
) {

    actual suspend fun download(
        downloadJobItem: DownloadJobItem,
        fetchProgressListener: FetchProgressListener,
    ) {
        try {
            val url = downloadJobItem.djiOriginUrl
                ?: throw IllegalArgumentException("Null URL on ${downloadJobItem.djiUid}")
            val destinationUri = downloadJobItem.djiDestPath?.let { DoorUri.parse(it) }
                ?: throw IllegalArgumentException("Null destination uri on ${downloadJobItem.djiUid}")

            val request = Request.Builder()
                .url(url)
                .build()


            //TODO: wrap this and run it asynchronously instead
            okHttpClient.newCall(request).execute().use { response ->
                if(response.code != 200) {
                    throw IOException("$url response code was ${response.code} : expected 200 OK response")
                }

                val totalBytes = response.header("content-length") ?.toLong()
                    ?: throw IllegalStateException("$url does not provide a content-length header.")
                fetchProgressListener.onFetchProgress(FetchProgressEvent(downloadJobItem.djiUid, 0,
                    totalBytes))

                val body = response.body ?: throw IllegalStateException("Response to $url has no body!")
                body.byteStream().use { bodyIn ->
                    FileOutputStream(destinationUri.toFile()).use { fileOut ->
                        bodyIn.copyToAndUpdateProgress(fileOut, fetchProgressListener, downloadJobItem.djiUid,
                            totalBytes)
                    }
                }
            }

        }catch(e: Exception) {
            throw e
        }
    }

}