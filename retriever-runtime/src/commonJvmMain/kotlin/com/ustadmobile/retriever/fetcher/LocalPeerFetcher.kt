package com.ustadmobile.retriever.fetcher

import com.ustadmobile.door.ext.writeToFile
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.IntegrityChecksum
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.RetrieverStatusUpdateEvent
import com.ustadmobile.retriever.ext.requirePostfix
import com.ustadmobile.retriever.io.extractToDir
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import com.ustadmobile.retriever.ext.headerSize
import java.io.*
import java.util.*
import io.github.aakira.napier.Napier

actual class LocalPeerFetcher(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val integrityChecksums: Array<IntegrityChecksum> = IntegrityChecksum.values(),
) {

    actual suspend fun download(
        endpointUrl: String,
        downloadJobItems: List<DownloadJobItem>,
        retrieverListener: RetrieverListener
    ): FetchResult {
        //Validate input here

        var firstFileTmp: File? = null
        val firstFile = File(downloadJobItems[0].djiDestPath!!)
        val jobItemUrlMap = downloadJobItems.associateBy { it.djiOriginUrl!! }
        val listenerWrapper = object: RetrieverListener {
            override suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent) {
                retrieverListener.onRetrieverProgress(retrieverProgressEvent)
            }

            override suspend fun onRetrieverStatusUpdate(retrieverStatusEvent: RetrieverStatusUpdateEvent) {
                retrieverListener.onRetrieverStatusUpdate(retrieverStatusEvent)
            }
        }

        try {
            val url = endpointUrl.requirePostfix("/") + "zipped"
            val originUrlsList = JsonArray(downloadJobItems.map { JsonPrimitive(it.djiOriginUrl) })

            //move first file out of the way, otherwise the extractor will attempt
            val firstFileZipHeader = File(firstFile.parentFile, "${firstFile.name}.zipentry")
            val bytesAlreadyDownloaded = firstFile.length()
            if(bytesAlreadyDownloaded > 0){
                //we need to get the zip header for this - maybe we should have an endpoint just for this?
                // no because this can be turned into a multi range setup
                val zipHeaderSize = ZipEntry(downloadJobItems.first().djiOriginUrl!!).headerSize
                val zipHeaderRequest = Request.Builder()
                    .url(url)
                    .method("POST", json.encodeToString(
                        JsonArray.serializer(),
                        JsonArray(listOf(JsonPrimitive(downloadJobItems.first().djiOriginUrl!!)))
                    )
                            .toRequestBody("application/json".toMediaType()))
                    .addHeader("range", "bytes=0-${zipHeaderSize-1}") //Ranges are inclusive
                    .build()

                okHttpClient.newCall(zipHeaderRequest).execute().use { response ->
                    val body = response.body ?: throw IllegalStateException("Zip header has no body!")
                    body.byteStream().use {
                        it.writeToFile(firstFileZipHeader)
                    }
                }

                firstFileTmp = File(firstFile.parentFile, firstFile.name + ".tmp")

                //now try and move the first file itself
                if(!firstFile.renameTo(firstFileTmp))
                    throw IOException("Could not rename $firstFile to $firstFileTmp")
            }

            val request = Request.Builder()
                .url(url)
                .method("POST", json.encodeToString(JsonArray.serializer(), originUrlsList)
                    .toRequestBody(contentType = "application/json".toMediaType()))
                .apply {
                    if(firstFileTmp != null)
                        addHeader("range", "bytes=${firstFileZipHeader.length() + firstFileTmp.length()}-")
                }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if(bytesAlreadyDownloaded == 0L && response.code != 200)
                    throw IllegalStateException("Expected 200 OK response: got ${response.code}")
                else if(bytesAlreadyDownloaded > 0 && response.code != 206)
                    throw IllegalStateException("Expected 206 partial content response: got ${response.code}")

                val body = response.body ?: throw IllegalStateException("Response has no body!")

                val destFileProvider: (ZipEntry) -> File = { zipEntry ->
                    jobItemUrlMap[zipEntry.name]?.djiDestPath?.let { File(it) }
                        ?: throw IllegalArgumentException("Unexpected entry in result stream: ${zipEntry.name}")
                }

                val sourceInput = if(firstFileTmp != null) {
                    SequenceInputStream(Vector<InputStream>(3).apply {
                        addElement(FileInputStream(firstFileZipHeader))
                        addElement(FileInputStream(firstFileTmp))
                        addElement(body.byteStream())
                    }.elements())
                }else {
                    body.byteStream()
                }

                ZipInputStream(sourceInput).use { zipIn ->
                    zipIn.extractToDir(destFileProvider, jobItemUrlMap, integrityChecksums = integrityChecksums,
                        progressListener = listenerWrapper)
                }
            }

            return FetchResult()
        }catch (e: Exception) {
            Napier.e("Exception attempting to download from peer", e, tag= Retriever.LOGTAG)
            throw e
        }finally {
            if(firstFileTmp?.exists() == true && firstFile.exists())
                firstFileTmp.delete()
        }

    }
}