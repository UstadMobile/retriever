package com.ustadmobile.retriever.responder

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import com.google.gson.Gson
import com.ustadmobile.lib.db.entities.AvailableFile
import com.ustadmobile.retriever.db.RetrieverDatabase

class RequestResponder:RouterNanoHTTPD.UriResponder{

    private fun newBadRequestResponse(errorMessage: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", errorMessage)


    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {

        //TODO: Actual work
        val db = uriResource.initParameter(PARAM_DB_INDEX, RetrieverDatabase::class.java)

        urlParams[PARAM_FILE_REQUEST_URL]?: newBadRequestResponse("No file url requested.")
        val fileUrl: String?  = session.parameters.get(PARAM_FILE_REQUEST_URL)?.firstOrNull()?.toString()
        val fileAvailability: List<AvailableFile> = db.availableFileDao.isFileAvailable(fileUrl?:"")

        val status = if (fileAvailability.isEmpty())
            NanoHTTPD.Response.Status.NOT_FOUND
        else
            NanoHTTPD.Response.Status.OK

        val gson = Gson()
        return NanoHTTPD.newFixedLengthResponse(
            status, "application/json", gson.toJson(fileAvailability))
    }

    override fun put(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        TODO("Not yet implemented")
    }

    override fun post(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        TODO("Not yet implemented")
    }

    override fun delete(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        TODO("Not yet implemented")
    }

    override fun other(
        method: String?,
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        TODO("Not yet implemented")
    }

    companion object{
        const val PARAM_FILE_REQUEST_URL = "fileUrl"
        const val PARAM_DB_INDEX = 0
    }
}