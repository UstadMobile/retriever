package com.ustadmobile.retriever

import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.fetcher.RetrieverListener
import com.ustadmobile.retriever.util.ReverseProxyDispatcher
import fi.iki.elonen.router.RouterNanoHTTPD
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class RetrieverIntegrationTest {

    lateinit var retrieverPeers : List<RetrieverJvm>

    lateinit var peerTmpFolders: List<File>

    lateinit var originServer: RouterNanoHTTPD

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var httpClient: HttpClient

    private lateinit var json: Json

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())
        json = Json {
            encodeDefaults = true
        }

        okHttpClient = OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().also {
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

        retrieverPeers.forEach {
            runBlocking { it.awaitServer() }
        }

        peerTmpFolders = retrieverPeers.map { temporaryFolder.newFolder() }

    }

    private fun mockDiscoverPeers() {
        runBlocking {
            //Make peers discover each other
            retrieverPeers.forEachIndexed { index, peer ->
                retrieverPeers.forEachIndexed { otherIndex, otherPeer ->
                    val otherPeerServer: RouterNanoHTTPD = runBlocking { otherPeer.awaitServer() }
                    if(index != otherIndex) {
                        peer.handleNodeDiscovered(NetworkNode().apply {
                            networkNodeEndpointUrl = "http://127.0.0.1:${otherPeerServer.listeningPort}/"
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

    private fun RetrieverJvm.assertDownloadMatchesOriginal(originUrl: String) {
        val resPath = originUrl.substringAfterLast("/")
        val resBytes = this@RetrieverIntegrationTest::class.java.getResourceAsStream("/$resPath")!!.readAllBytes()
        val peerDownloadJobItem = runBlocking { db.downloadJobItemDao.findByUrlFirstOrNull(originUrl) }
            ?: throw IllegalStateException("Download for $originUrl not found!")
        val downloadedBytes = File(peerDownloadJobItem.djiDestPath!!).readBytes()
        Assert.assertArrayEquals("Bytes of download $originUrl match original resource $resPath",
            resBytes, downloadedBytes)
    }

    private fun RetrieverJvm.assertAllDownloadedLocally(originUrl: String) {
        val peerDownloadJobItem = runBlocking { db.downloadJobItemDao.findByUrlFirstOrNull(originUrl) }
            ?: throw IllegalStateException("Download for $originUrl not found!")
        Assert.assertEquals("$originUrl was downloaded entirely from peer",
            peerDownloadJobItem.djiTotalSize, peerDownloadJobItem.djiLocalBytesSoFar)
    }

    private fun RetrieverJvm.assertAllDownloadedFromOrigin(originUrl: String) {
        val peerDownloadJobItem = runBlocking { db.downloadJobItemDao.findByUrlFirstOrNull(originUrl) }
            ?: throw IllegalStateException("Download for $originUrl not found!")
        Assert.assertEquals("$originUrl was downloaded entirely from peer",
            peerDownloadJobItem.djiTotalSize, peerDownloadJobItem.djiOriginBytesSoFar)
    }

    @Test
    fun givenPeerOnlineWithFileAvailable_whenOtherPeerDownloads_thenShouldFetchFromOtherPeer() {
        mockDiscoverPeers()

        val originUrl = originServer.url("/resources/cat-pic0.jpg")

        val downloadFromOriginRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[0], "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[0].retrieve(listOf(downloadFromOriginRequest), mock { })
        }

        val downloadFromPeerRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[1], "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[1].retrieve(listOf(downloadFromPeerRequest), mock { })
        }

        retrieverPeers[1].assertDownloadMatchesOriginal(originUrl)
        retrieverPeers[1].assertAllDownloadedLocally(originUrl)
    }

    @Test
    fun givenNoOtherPeerDiscovered_whenDownloadCalled_thenShouldDownFromOrigin() {

        val originUrl = originServer.url("/resources/cat-pic0.jpg")

        val downloadFromOriginRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[0], "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[0].retrieve(listOf(downloadFromOriginRequest), mock { })
        }

        retrieverPeers[0].assertDownloadMatchesOriginal(originUrl)
        retrieverPeers[0].assertAllDownloadedFromOrigin(originUrl)
    }

    @Test
    fun givenOriginServerOfflineAndPeerHasFile_whenDownloaded_thenShouldSucceed() {
        mockDiscoverPeers()

        val originUrl = originServer.url("/resources/cat-pic0.jpg")
        val downloadFromOriginRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[0], "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[0].retrieve(listOf(downloadFromOriginRequest), mock { })
        }

        //Simulate origin serve roffline
        originServer.stop()

        val downloadFromPeerRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[1], "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[1].retrieve(listOf(downloadFromPeerRequest), mock { })
        }

        retrieverPeers[1].assertDownloadMatchesOriginal(originUrl)
        retrieverPeers[1].assertAllDownloadedLocally(originUrl)
    }

    @Test
    fun givenOriginServerOfflineAndNoOtherPeerDiscovered_whenDownloaded_thenShouldFail() {
        val originUrl = originServer.url("/resources/cat-pic0.jpg")
        val downloadFromOriginRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[0], "cat-pic0.jpg").absolutePath, null)

        originServer.stop()

        val retrieverListener: RetrieverListener = mock { }

        runBlocking {
            retrieverPeers[0].retrieve(listOf(downloadFromOriginRequest), retrieverListener)
        }

        verifyBlocking(retrieverListener) {
            onRetrieverStatusUpdate(argWhere { it.status == Retriever.STATUS_FAILED })
        }

    }

    @Test
    fun givenDownloadStarted_whenPeerLostMidDownload_thenShouldDownloadFromOrigin() {
        val mockLostPeerServer = MockWebServer()
        val successRequestsRemaining = AtomicInteger(1)
        val peer0Server = runBlocking { retrieverPeers[0].awaitServer() }

        val proxyDispatcher = ReverseProxyDispatcher(peer0Server.url("/").toHttpUrl())
        mockLostPeerServer.dispatcher = object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if(request.path?.endsWith("/availability") == true) {
                    val availability: String = runBlocking {
                        httpClient.post(peer0Server.url("/availability")) {
                            body = ByteArrayContent(request.body.readByteArray(),
                                contentType = ContentType.Application.Json)
                        }
                    }

                    return MockResponse()
                        .setResponseCode(200)
                        .setBody(availability)
                }else if(request.path?.endsWith("/zipped") == true) {
                    if(successRequestsRemaining.getAndDecrement() > 0) {
                        return proxyDispatcher.dispatch(request)
                    }else {
                        return MockResponse()
                            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                    }

                }else{
                    return MockResponse()
                        .setResponseCode(404)
                }
            }
        }


        //Make peer 0 download the file from origin server
        val originUrl = originServer.url("/resources/cat-pic0.jpg")

        val downloadFromOriginRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[0], "cat-pic0.jpg").absolutePath, null)

        runBlocking {
            retrieverPeers[0].retrieve(listOf(downloadFromOriginRequest), mock { })
        }

        runBlocking {
            retrieverPeers[1].handleNodeDiscovered(NetworkNode().apply {
                networkNodeEndpointUrl = mockLostPeerServer.url("/").toString()
                networkNodeDiscovered = systemTimeInMillis()
            })
        }

        val downloadFromPeerRequest = RetrieverRequest(originUrl,
            File(peerTmpFolders[1], "cat-pic0.jpg").absolutePath, null)

        //Should download partially from peer, then from origin
        runBlocking {
            retrieverPeers[1].retrieve(listOf(downloadFromPeerRequest), mock { })
        }

        retrieverPeers[1].assertDownloadMatchesOriginal(originUrl)
    }

    @Test
    fun givenNetworkNodeDiscoveredByPeer_whenOtherNodeLost_willBeDetectedAsLostByPing() {

    }

}