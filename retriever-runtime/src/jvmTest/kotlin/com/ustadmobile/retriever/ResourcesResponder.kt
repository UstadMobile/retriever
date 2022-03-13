package com.ustadmobile.retriever

import com.ustadmobile.retriever.responder.FileResponder
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD

/**
 * Simple NanoHTTPD responder that will respond using resources in the classpath.
 *
 */
class ResourcesResponder() : RouterNanoHTTPD.UriResponder {

    private fun respondFromResource(
        uriResource: RouterNanoHTTPD.UriResource,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        val prefix = uriResource.initParameter(String::class.java)
        val resourceFileSource = ResourceFileSource(this::class.java, session.uri.substringAfter(prefix))
        return FileResponder.newResponseFromFile(uriResource, session, resourceFileSource)
    }

    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response = respondFromResource(uriResource, session)

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
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response = respondFromResource(uriResource, session)
}