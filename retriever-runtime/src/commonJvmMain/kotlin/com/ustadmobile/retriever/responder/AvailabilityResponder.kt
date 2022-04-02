package com.ustadmobile.retriever.responder

import com.ustadmobile.retriever.FileAvailableResponse
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.receiveRequestBody
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import com.ustadmobile.door.ext.withDoorTransaction
import kotlinx.serialization.builtins.ListSerializer

class AvailabilityResponder:RouterNanoHTTPD.UriResponder{

    private fun newBadRequestResponse(errorMessage: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", errorMessage)


    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ) = newBadRequestResponse("Method not supported: GET")


    override fun post(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        //receive request body
        val jsonText = session.receiveRequestBody()
            ?: return newBadRequestResponse("No request body to specify urls to check availability")

        val jsonArray: List<JsonElement> = Json.decodeFromString(JsonArray.serializer(), jsonText)
        val fileUrlList : List<String> = jsonArray.map { (it as JsonPrimitive).content }
        if(fileUrlList.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain",
                "Empty list urls to retrieve")
        }

        val db: RetrieverDatabase = uriResource.initParameter(PARAM_DB_INDEX, RetrieverDatabase::class.java)
        val json: Json = uriResource.initParameter(PARAM_JSON_INDEX, Json::class.java)
        val locallyStoredFiles = db.withDoorTransaction(RetrieverDatabase::class) { txDb ->
            fileUrlList.chunked(100).flatMap {
                db.locallyStoredFileDao.findLocallyStoredFilesByUrlList(it)
            }
        }

        val availabilityResponseList: List<FileAvailableResponse> =
            locallyStoredFiles.map {
                FileAvailableResponse(it.lsfOriginUrl?:"", "sha256", it.lsfFileSize)
            }

        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json",
            json.encodeToString(ListSerializer(FileAvailableResponse.serializer()), availabilityResponseList))
    }

    override fun put(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response =newBadRequestResponse("Method not supported")

    override fun delete(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response = newBadRequestResponse("Method not supported")

    override fun other(
        method: String?,
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response  = newBadRequestResponse("Method not supported")

    companion object{
        const val PARAM_FILE_REQUEST_URL = "fileUrl"
        const val PARAM_DB_INDEX = 0
        const val PARAM_JSON_INDEX = 1
    }
}