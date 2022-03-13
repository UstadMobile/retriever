package com.ustadmobile.retriever.responder

import com.ustadmobile.retriever.ext.requirePostfix
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import java.io.File

class DirUriResponder: RouterNanoHTTPD.UriResponder {

    private fun serve(
        uriResource: RouterNanoHTTPD.UriResource,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        val baseDir = uriResource.initParameter(File::class.java)
        val prefix = uriResource.initParameter(String::class.java)

        val responseFile = File(baseDir, session.uri.substringAfter(prefix.requirePostfix("/")))
        return FileResponder.newResponseFromFile(uriResource, session, FileResponder.FileSource(responseFile))
    }

    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response = serve(uriResource, session)

    override fun put(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        TODO("Not yet implemented")
    }

    override fun post(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response = serve(uriResource, session)

    override fun delete(
        uriResource: RouterNanoHTTPD.UriResource?,
        urlParams: MutableMap<String, String>?,
        session: NanoHTTPD.IHTTPSession?
    ): NanoHTTPD.Response {
        TODO("Not yet implemented")
    }

    override fun other(
        method: String,
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response = serve(uriResource, session)
}