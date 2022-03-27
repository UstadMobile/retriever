package com.ustadmobile.retriever

import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.ext.url
import fi.iki.elonen.router.RouterNanoHTTPD
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File

class RetrieverIntegrationTest {

    lateinit var retrieverPeers : List<RetrieverJvm>

    lateinit var originServer: RouterNanoHTTPD

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var httpClient: HttpClient

    private lateinit var json: Json

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        json = Json {
            encodeDefaults = true
        }

        okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()


        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)
            install(HttpTimeout)
            engine {
                preconfigured = okHttpClient
            }
        }

        originServer = RouterNanoHTTPD(0)
        originServer.addRoute("/resources/.*", ResourcesResponder::class.java,
            "/resources")
        originServer.start()

        retrieverPeers = (0..1).map { peerIndex ->
            RetrieverBuilder.builder("retriever", httpClient, okHttpClient, json) {
                dbName = "RetrieverPeerDb$peerIndex"
            }.build() as RetrieverJvm
        }


    }

    private fun mockDiscoverPeers() {
        runBlocking {
            //Make peers discover each other
            retrieverPeers.forEachIndexed { index, peer ->
                retrieverPeers.forEachIndexed { otherIndex, otherPeer ->
                    if(index != otherIndex) {
                        peer.addNewNode(NetworkNode().apply {
                            networkNodeEndpointUrl = "http://127.0.0.1:${otherPeer.server.listeningPort}/"
                            networkNodeDiscovered = systemTimeInMillis()
                        })
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        originServer.stop()
        retrieverPeers.forEach {
            it.close()
        }
    }

    @Test
    fun givenPeerOnlineWithFileAvailable_whenOtherPeerDownloads_thenShouldFetchFromOtherPeer() {
        mockDiscoverPeers()

        val peer0Dest = temporaryFolder.newFolder()
        val peer1Dest = temporaryFolder.newFolder()

        val originUrl = originServer.url("/resources/cat-pic0.jpg")

        val downloadFromOriginRequest = RetrieverRequest(originUrl,
            File(peer0Dest, "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[0].retrieve(listOf(downloadFromOriginRequest), mock { })
        }

        val downloadFromPeerRequest = RetrieverRequest(originUrl,
            File(peer1Dest, "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[1].retrieve(listOf(downloadFromPeerRequest), mock { })
        }

        val peer1DownloadjobItem = runBlocking {
            retrieverPeers[1].db.downloadJobItemDao.findByUrlFirstOrNull(originUrl)
        }

        Assert.assertEquals("Peer 1 downloaded item from peer",
            peer1DownloadjobItem!!.djiTotalSize, peer1DownloadjobItem!!.djiLocalBytesSoFar)

    }


}