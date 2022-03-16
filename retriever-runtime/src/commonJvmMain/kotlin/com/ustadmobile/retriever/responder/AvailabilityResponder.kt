package com.ustadmobile.retriever.responder

import com.google.gson.Gson
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.receiveRequestBody
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class AvailabilityResponder:RouterNanoHTTPD.UriResponder{

    private fun newBadRequestResponse(errorMessage: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", errorMessage)


    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {

        val db = uriResource.initParameter(PARAM_DB_INDEX, RetrieverDatabase::class.java)

        urlParams[PARAM_FILE_REQUEST_URL]?: newBadRequestResponse("No file url requested.")
        val fileUrl: String?  = session.parameters.get(PARAM_FILE_REQUEST_URL)?.firstOrNull()?.toString()
        val fileAvailability: List<LocallyStoredFile> = db.locallyStoredFileDao.isFileAvailable(fileUrl?:"")

        val status = if (fileAvailability.isEmpty())
            NanoHTTPD.Response.Status.NOT_FOUND
        else
            NanoHTTPD.Response.Status.OK

        val gson = Gson()
        return NanoHTTPD.newFixedLengthResponse(
            status, "application/json", gson.toJson(fileAvailability))
    }

    data class FileAvailableResponse(val originUrl: String, val sha256: String, val size: Long)

    override fun post(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {

        //receive request body

        val jsonText = session.receiveRequestBody()

        val jsonQueryParam2 = session.queryParameterString

        val jsonQueryParam = session.queryParameterString
            ?: return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
            "text/plain",
            "No request body")

        val jsonArray: List<JsonElement> = Json.decodeFromString(JsonArray.serializer(), jsonQueryParam)
        val fileUrlList : List<String> = jsonArray.map { (it as JsonPrimitive).content }
        if(fileUrlList.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain",
                "Empty list urls to retrieve")
        }

        val db: RetrieverDatabase = uriResource.initParameter(RetrieverDatabase::class.java)
        val locallyStoredFiles = fileUrlList.chunked(100).flatMap {
//            db.withDoorTransactionInternal(RetrieverDatabase::class){
//                txDb -> txDb.locallyStoredFileDao.findAvailableFilesByUrlList(it)
//
//            }
            db.locallyStoredFileDao.findAvailableFilesByUrlList(it)
        }


        val availabilityResponseList: List<FileAvailableResponse> =
            locallyStoredFiles.map {
                FileAvailableResponse(it.lsfOriginUrl?:"", "sha256", it.lsfFileSize)
            }

        val gson = Gson()
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", gson.toJson(availabilityResponseList))


    }

    override fun put(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, "application/json", Gson().toJson(""))
    }

    override fun delete(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, "application/json", Gson().toJson(""))
    }

    override fun other(
        method: String?,
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, "application/json", Gson().toJson(""))
    }

    companion object{
        const val PARAM_FILE_REQUEST_URL = "fileUrl"
        const val   PARAM_DB_INDEX = 0
    }
}