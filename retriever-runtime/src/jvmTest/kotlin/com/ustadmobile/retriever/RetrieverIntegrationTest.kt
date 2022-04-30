package com.ustadmobile.retriever

import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.fetcher.RetrieverListener
import com.ustadmobile.retriever.util.ReverseProxyDispatcher
import com.ustadmobile.retriever.util.assertSuccessfullyCompleted
import com.ustadmobile.retriever.util.h5pDownloadJobItemList
import com.ustadmobile.retriever.util.waitUntilOrTimeout
import fi.iki.elonen.NanoHTTPD
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
import kotlin.system.measureTimeMillis

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
                pingInterval = 1000
                pingRetryInterval = 500
                strikeOffTimeWindow = 3000
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

    private fun RetrieverJvm.assertAllDownloadedLocally(
        originUrl: String,
        txDb: RetrieverDatabase = db,
    ) {
        val peerDownloadJobItem = runBlocking { txDb.downloadJobItemDao.findByUrlFirstOrNull(originUrl) }
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


    private fun RetrieverJvm.waitForOtherNetworkNode(
        timeout: Long,
        otherEndpointUrl: String,
        check: (NetworkNode?) -> Boolean
    ) : NetworkNode? {
        return runBlocking {
            db.waitUntilOrTimeout(timeout, listOf("NetworkNode")) {
                check(it.networkNodeDao.findByEndpointUrl(otherEndpointUrl))
            }

            db.networkNodeDao.findByEndpointUrl(otherEndpointUrl)
        }
    }

    private fun RetrieverJvm.endpointUrl(): String {
        return runBlocking { awaitServer() }.url("/")
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

    /**
     * Simulate a large download batch with 1700+ files. First download from the origin server, then check that the
     * second peer can download all of them from the peer.
     */
    @Test
    fun givenPeerOnlineWithH5pContainerAvaailable_whenOtherPeerDownloads_thenShouldFetchFromOtherPeer() {
        mockDiscoverPeers()

        val jobItems0 = h5pDownloadJobItemList(peerTmpFolders[0]) {
            originServer.url("/resources$it")
        }

        val mockProgressListener0 : RetrieverListener = mock { }
        runBlocking {
            val downloadTime = measureTimeMillis {
                retrieverPeers[0].retrieve(jobItems0.map {
                    RetrieverRequest(it.djiOriginUrl!!, it.djiDestPath!!, it.djiIntegrity!!)
                }, mockProgressListener0)
            }
            println("Downloaded from origin in: $downloadTime ms")
        }

        jobItems0.forEach {
            it.assertSuccessfullyCompleted(mockProgressListener0, originServer.url("/resources/"))
        }

        val downloadFromPeerItems = h5pDownloadJobItemList(peerTmpFolders[1]) {
            originServer.url("/resources$it")
        }

        val mockProgressListener1: RetrieverListener = mock { }
        runBlocking {
            val downloadTime = measureTimeMillis {
                retrieverPeers[1].retrieve(downloadFromPeerItems.map {
                    RetrieverRequest(it.djiOriginUrl!!, it.djiDestPath!!, it.djiIntegrity!!)
                }, mockProgressListener1)
            }
            println("Downloaded from peer in: $downloadTime ms")
        }

        downloadFromPeerItems.forEach {
            it.assertSuccessfullyCompleted(mockProgressListener1, originServer.url("/resources/"))
            retrieverPeers[1].assertAllDownloadedLocally(it.djiOriginUrl!!)
        }
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
    fun givenNetworkNodeDiscoveredByPeer_whenNetworkNodeIsLost_willBeDetectedAsLostByPingAndStruckOff() {
        val peer1Url = runBlocking { "http://127.0.0.1:${retrieverPeers[1].listeningPort()}/" }
        runBlocking {
            retrieverPeers[0].handleNodeDiscovered(NetworkNode().apply {
                networkNodeEndpointUrl = peer1Url
                networkNodeDiscovered = systemTimeInMillis()
            })
        }

        retrieverPeers[0].waitForOtherNetworkNode(2000, peer1Url) { it != null }

        val peer1Server: NanoHTTPD = runBlocking { retrieverPeers[1].awaitServer() }
        peer1Server.stop()

        val peer1InPeer0Db = retrieverPeers[0].waitForOtherNetworkNode(5000, peer1Url) {
            it?.networkNodeStatus == NetworkNode.STATUS_STRUCK_OFF
        }

        Assert.assertEquals("After being switched off, peer 1 is now struck off", NetworkNode.STATUS_STRUCK_OFF,
            peer1InPeer0Db?.networkNodeStatus ?: -1)
    }

    /**
     * Test the "belt and suspenders" approach to discovery. If Node A has discovered B, but Node B has not discovered
     * Node A, then Node B can learn of the existence of Node A via handling an incoming ping request
     */
    @Test
    fun givenTwoNetworkNodes_whenOneDiscoversTheOther_otherWillMarkFirstAsDiscoveredWhenReceivingPing() {
        val peer1Url = runBlocking { "http://127.0.0.1:${retrieverPeers[1].listeningPort()}/" }
        val peer0Url = runBlocking {  "http://127.0.0.1:${retrieverPeers[0].listeningPort()}/" }
        runBlocking {
            retrieverPeers[0].handleNodeDiscovered(NetworkNode().apply {
                networkNodeEndpointUrl = peer1Url
                networkNodeDiscovered = systemTimeInMillis()
            })
        }

        val peer0InPeer1Db = retrieverPeers[1].waitForOtherNetworkNode(2000, peer0Url) { it != null }

        Assert.assertEquals("Peer 1 will discover peer 0 by receiving an incoming ping, even without service discovery",
            peer0Url, peer0InPeer1Db?.networkNodeEndpointUrl)
    }


    @Test
    fun givenTwoNodesDiscoveredEachOther_whenOneGoesOfflineAndComesBack_thenWillBeStruckOffAndRestored() {
        mockDiscoverPeers()

        val peer1Server: NanoHTTPD = runBlocking { retrieverPeers[1].awaitServer() }
        val peer1Url = retrieverPeers[1].endpointUrl()

        retrieverPeers[0].waitForOtherNetworkNode(3000, peer1Url) {
            it != null
        }

        peer1Server.stop()

        val struckOffInDb = retrieverPeers[0].waitForOtherNetworkNode(4000, peer1Url) {
            it?.networkNodeStatus == NetworkNode.STATUS_STRUCK_OFF
        }

        peer1Server.start()

        val restoredInDb = retrieverPeers[0].waitForOtherNetworkNode(4000, peer1Url) {
            it?.networkNodeStatus == NetworkNode.STATUS_OK
        }

        Assert.assertEquals("Peer was struck off after it went offline", NetworkNode.STATUS_STRUCK_OFF,
            struckOffInDb?.networkNodeStatus ?: -1)

        Assert.assertEquals("Peer was restored after it came back online", NetworkNode.STATUS_OK,
            restoredInDb?.networkNodeStatus ?: -1)
    }


}