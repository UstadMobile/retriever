package com.ustadmobile.retriever.responder

import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.RetrieverCommon
import com.ustadmobile.retriever.RetrieverCommon.Companion.RETRIEVER_PORT_HEADER
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * The ping responder will provide a simple 204 no content response to all incoming requests.
 *
 * It will also check and make sure that this node has registered the discovery of the node that has just made the ping,
 * and update the lastSuccessTime for the remoteNode. This is a "belt and suspenders" approach to discovering nodes.
 */
class PingResponder : AbstractUriResponder() {

    override fun get(
        uriResource: RouterNanoHTTPD.UriResource,
        urlParams: MutableMap<String, String>,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        val retrieverCommon = uriResource.initParameter(RetrieverCommon::class.java)
        val requesterRetrieverPort = session.headers[RETRIEVER_PORT_HEADER]?.toInt() ?: -1
        if(requesterRetrieverPort > 0) {
            val remoteEndpoint = "http://${session.remoteIpAddress}:$requesterRetrieverPort/"
            runBlocking {
                retrieverCommon.handleNodeDiscovered(NetworkNode().apply {
                    networkNodeEndpointUrl = remoteEndpoint
                })
            }
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain","")
    }
}