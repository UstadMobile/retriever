package com.ustadmobile.retriever.responder

import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.receiveRequestBody
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import java.util.zip.ZipEntry
import com.ustadmobile.retriever.ext.totalZipSize
import kotlinx.coroutines.*
import java.io.*
import com.ustadmobile.door.ext.withDoorTransaction

class ZippedItemsResponder : AbstractUriResponder(){

    override fun post(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        //receive the list of files here
        val jsonText = session.receiveRequestBody()
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain",
                "No request body")

        val jsonArray: List<JsonElement> = Json.decodeFromString(JsonArray.serializer(), jsonText)
        val originUrlList : List<String> = jsonArray.map { (it as JsonPrimitive).content }
        if(originUrlList.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain",
                "Empty list urls to retrieve")
        }

        val db: RetrieverDatabase = uriResource.initParameter(RetrieverDatabase::class.java)

        val locallyStoredFiles = db.withDoorTransaction(RetrieverDatabase::class) { txDb ->
            originUrlList.chunked(100).flatMap {
                txDb.locallyStoredFileDao.findAvailableFilesByUrlList(it)
            }
        }

        val nullOriginUrls = locallyStoredFiles.filter { it.lsfOriginUrl == null }
        if(nullOriginUrls.isNotEmpty())
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain",
                "Found null origin urls: ${nullOriginUrls.map { it.locallyStoredFileUid }.joinToString()}")

        //if there is something we don't have, return bad request.
        val locallyStoredOriginUrlMap = locallyStoredFiles.map { (it.lsfOriginUrl!!) to it }.toMap()
        val unavailableUrls = originUrlList.filter { !locallyStoredOriginUrlMap.containsKey(it) }
        if(unavailableUrls.isNotEmpty())
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain",
                "Don't have: ${unavailableUrls.joinToString()}")

        val locallyStoredFilesSorted = originUrlList.map { locallyStoredOriginUrlMap[it]!! }
        val zipEntries = locallyStoredFiles.map { storedFile ->
            val file = storedFile.lsfFilePath?.let { File(it) }
                ?: throw IllegalArgumentException("null file path for ${storedFile.lsfOriginUrl}")
            val originUrl = storedFile.lsfOriginUrl
                ?: throw IllegalArgumentException("null url for ${storedFile.locallyStoredFileUid}")
            val fileSize = file.length()
            storedFile.lsfOriginUrl!! to ZipEntry(originUrl).apply {
                comment = originUrl
                crc = storedFile.lsfCrc32
                compressedSize = fileSize
                size = fileSize
            }
        }.toMap()


        val pipedOutputStream = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOutputStream)

        GlobalScope.launch {
            try {
                ZipOutputStream(pipedOutputStream).use { zipOut ->
                    zipOut.setMethod(ZipOutputStream.STORED)
                    locallyStoredFilesSorted.forEach { storedFile ->
                        zipOut.putNextEntry(zipEntries[storedFile.lsfOriginUrl!!]!!)
                        FileInputStream(File(storedFile.lsfFilePath!!)).use { fileIn ->
                            fileIn.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }catch(e: IOException) {
                pipedOutputStream.close()
                pipedIn.close()
                throw e
            }

        }

        val totalSize = zipEntries.values.toList().totalZipSize(null)
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/zip",
            pipedIn, totalSize).also {
                it.addHeader("content-length", totalSize.toString())
        }
    }
}