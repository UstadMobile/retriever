package com.ustadmobile.retriever.responder

import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import java.io.File

/**
 * Responder that will answer the singular file download endpoint
 */
class SingleItemResponder : RouterNanoHTTPD.UriResponder{

    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        val db = uriResource.initParameter(RetrieverDatabase::class.java)
        val originUrl = session.parameters["originUrl"]?.firstOrNull() ?:
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain", "Bad Request: no origin url specified")

        val availableFilePath: LocallyStoredFile = db.locallyStoredFileDao.findStoredFile(originUrl) ?:
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain", "Not found: file with origin url = $originUrl")

        return FileResponder.newResponseFromFile(
            NanoHTTPD.Method.GET, uriResource, session,
            FileResponder.FileSource(File(availableFilePath.lsfFilePath ?: ""))
        )


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


    companion object {

        const val PARAM_DB_INDEX = 0

    }
}