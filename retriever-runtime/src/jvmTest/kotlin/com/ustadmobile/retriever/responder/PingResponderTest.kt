package com.ustadmobile.retriever.responder

import com.ustadmobile.retriever.RetrieverCommon
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import org.junit.Test
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

class PingResponderTest {

    @Test
    fun givenIncomingGet_whenRequesterPortHeaderPresenter_thenShouldCallHandleNodeDiscovere() {
        val mockRetriever = mock<RetrieverCommon> {}
        val mockSession = mock<NanoHTTPD.IHTTPSession> {
            on { headers }.thenReturn(mapOf(RetrieverCommon.RETRIEVER_PORT_HEADER to "1234"))
            on { remoteIpAddress }.thenReturn("192.168.1.2")
        }

        val mockUriResource = mock<RouterNanoHTTPD.UriResource> {
            on { initParameter(RetrieverCommon::class.java) }.thenReturn(mockRetriever)
        }

        val pingerUriResponder = PingResponder()
        pingerUriResponder.get(mockUriResource, mutableMapOf(), mockSession)

        verifyBlocking(mockRetriever) {
            handleNodeDiscovered(argWhere {
                it.networkNodeEndpointUrl == "http://192.168.1.2:1234/"
            })
        }
    }

}