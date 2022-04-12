package com.ustadmobile.retriever

import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.responder.PingResponder
import fi.iki.elonen.router.RouterNanoHTTPD
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

class PingerHttpPingResponderIntegrationTest {

    private lateinit var httpClient: HttpClient

    private lateinit var pingReceiverHttpd: RouterNanoHTTPD

    @Before
    fun setup() {
        httpClient = HttpClient(OkHttp) {
            install(HttpTimeout)
            install(ContentNegotiation)
        }

        pingReceiverHttpd = RouterNanoHTTPD(0)
    }

    @After
    fun tearDown() {
        httpClient.close()
        pingReceiverHttpd.stop()
    }

    @Test
    fun givenNodeActive_whenPingRequestMade_thenReceivingNodeShouldCallOnHandleNodeDiscovered() {
        val pingSenderLocalPort = 1234
        val pingReceiverMockRetrieverCommon = mock<RetrieverCommon> {
        }

        val pingSender = PingerHttp(httpClient)
        pingReceiverHttpd.addRoute("/ping", PingResponder::class.java, pingReceiverMockRetrieverCommon)
        pingReceiverHttpd.start()

        runBlocking {
            pingSender.ping(pingReceiverHttpd.url("/"), pingSenderLocalPort)
        }

        verifyBlocking(pingReceiverMockRetrieverCommon) {
            handleNodeDiscovered(argWhere {
                it.networkNodeEndpointUrl == "http://127.0.0.1:1234/"
            })
        }
    }

}