package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.ext.writeToFile
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.crc32
import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.fetcher.MultiItemFetcher
import com.ustadmobile.retriever.fetcher.SingleItemFetcher
import com.ustadmobile.retriever.responder.ZippedItemsResponder
import fi.iki.elonen.router.RouterNanoHTTPD
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class DownloaderFetcherIntegrationTest {

    private lateinit var originHttpServer: RouterNanoHTTPD

    private lateinit var peerHttpServer: RouterNanoHTTPD

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var downloadDestDir: File

    private lateinit var peerFilesDir: File

    private lateinit var localDb: RetrieverDatabase

    private lateinit var peerDb: RetrieverDatabase

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var singleItemFetcher: SingleItemFetcher

    private lateinit var multiItemFetcher: MultiItemFetcher

    private lateinit var json: Json

    private lateinit var itemsToDownload: List<DownloadJobItem>

    private var batchId = 0L

    private lateinit var mockAvailabilityManager: AvailabilityManager

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())
        originHttpServer = RouterNanoHTTPD(0)
        originHttpServer.addRoute("/resources/.*", ResourcesResponder::class.java, "/resources")
        originHttpServer.start()
        downloadDestDir = temporaryFolder.newFolder("downloads")
        peerFilesDir = temporaryFolder.newFolder("peer-files")
        localDb = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }
        peerDb = DatabaseBuilder.databaseBuilder(Any(),  RetrieverDatabase::class, "RetrieverDatabasePeer")
            .build().also {
                it.clearAllTables()
            }

        okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()

        json = Json { encodeDefaults = true }
        singleItemFetcher = SingleItemFetcher(okHttpClient)
        multiItemFetcher = MultiItemFetcher(okHttpClient, json)

        batchId = systemTimeInMillis()
        itemsToDownload = RESOURCE_PATHS.map { resPath ->
            DownloadJobItem().apply {
                djiBatchId = batchId
                djiOriginUrl = originHttpServer.url("/resources$resPath")
                djiDestPath = File(downloadDestDir, resPath.substringAfterLast("/")).absolutePath
                djiStatus = DownloadJobItem.STATUS_QUEUED
            }
        }

        runBlocking {
            localDb.downloadJobItemDao.insertList(itemsToDownload)
        }

        peerHttpServer = RouterNanoHTTPD(0)
        peerHttpServer.addRoute("/retriever/zipped", ZippedItemsResponder::class.java,
            peerDb)
        peerHttpServer.start()

        mockAvailabilityManager = mock { }
    }

    @Test
    fun givenOriginUrlsNotAvailableFromPeers_whenDownloadRuns_thenShouldDownloadFromOriginServer() {

        val downloader = Downloader(batchId, mockAvailabilityManager, { }, singleItemFetcher, multiItemFetcher, localDb)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        RESOURCE_PATHS.forEachIndexed { index, resPath ->
            Assert.assertArrayEquals(
                "Item $index downloaded bytes as expected",
                this::class.java.getResourceAsStream(resPath)!!.readAllBytes(),
                File(itemsToDownload[index].djiDestPath!!).readBytes())
        }
    }

    @Test
    fun givenOriginUrlAvailableFromPeer_whenDownloadRuns_thenShouldDownloadFromPeer() {
        val networkNode = NetworkNode().apply {
            networkNodeEndpointUrl = peerHttpServer.url("/retriever/")
            networkNodeId = localDb.networkNodeDao.insert(this)
        }

        localDb.availabilityResponseDao.insertList(itemsToDownload.map {
            AvailabilityResponse(networkNode.networkNodeId, it.djiOriginUrl!!, true, systemTimeInMillis())
        })

        peerDb.locallyStoredFileDao.insertList(
            RESOURCE_PATHS.map { resPath ->
                val localFile = File(peerFilesDir, resPath.substringAfter("/"))
                this@DownloaderFetcherIntegrationTest::class.java.getResourceAsStream(resPath)!!
                    .writeToFile(localFile)
                LocallyStoredFile(originHttpServer.url("/resources$resPath"), localFile.absolutePath,
                    localFile.length(), localFile.crc32)
            })

        val downloader = Downloader(batchId, mockAvailabilityManager, { }, singleItemFetcher, multiItemFetcher, localDb)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        RESOURCE_PATHS.forEachIndexed { index, resPath ->
            Assert.assertArrayEquals(
                "Item $index downloaded bytes as expected",
                this::class.java.getResourceAsStream(resPath)!!.readAllBytes(),
                File(itemsToDownload[index].djiDestPath!!).readBytes())
        }
    }

    companion object {

        val RESOURCE_PATHS = listOf("/cat-pic0.jpg", "/pigeon1.png")

    }
    
}