package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.toDoorUri
import com.ustadmobile.door.ext.toFile
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.fetcher.SingleItemFetcher
import fi.iki.elonen.router.RouterNanoHTTPD
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class DownloaderIntegrationTest {

    private lateinit var originHttpServer: RouterNanoHTTPD

    private lateinit var peerHttpServer: RouterNanoHTTPD

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var downloadDestDir: File

    private lateinit var localDb: RetrieverDatabase

    private lateinit var peerDb: RetrieverDatabase

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var singleItemFetcher: SingleItemFetcher

    @Before
    fun setup() {
        Napier.base(DebugAntilog())
        originHttpServer = RouterNanoHTTPD(0)
        originHttpServer.addRoute("/resources/.*", ResourcesResponder::class.java, "/resources")
        originHttpServer.start()
        downloadDestDir = temporaryFolder.newFolder("downloads")
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

        singleItemFetcher = SingleItemFetcher(okHttpClient)
    }

    @Test
    fun givenOriginUrlsNotAvailableFromPeers_whenDownloadRuns_thenShouldDownloadFromOriginServer() {
        val batchId = systemTimeInMillis()

        val itemsToDownload = listOf(DownloadJobItem().apply {
                djiBatchId = batchId
                djiOriginUrl = originHttpServer.url("/resources/cat-pic0.jpg")
                djiDestPath = File(downloadDestDir, "cat-pic0.jpg").toDoorUri().toString()
                djiStatus = DownloadJobItem.STATUS_QUEUED
            },
            DownloadJobItem().apply {
                djiBatchId = batchId
                djiOriginUrl = originHttpServer.url("/resources/pigeon1.png")
                djiDestPath = File(downloadDestDir, "pigeon1.png").toDoorUri().toString()
                djiStatus = DownloadJobItem.STATUS_QUEUED
            }
        )

        runBlocking {
            localDb.downloadJobItemDao.insertList(itemsToDownload)
        }

        val mockAvailabilityManager = mock<AvailabilityManager>()

        val downloader = Downloader(batchId, mockAvailabilityManager, { }, singleItemFetcher, localDb)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        Assert.assertArrayEquals(this::class.java.getResourceAsStream("/cat-pic0.jpg")!!.readAllBytes(),
            DoorUri.parse(itemsToDownload[0].djiDestPath!!).toFile().readBytes())
        Assert.assertArrayEquals(this::class.java.getResourceAsStream("/pigeon1.png")!!.readAllBytes(),
            DoorUri.parse(itemsToDownload[1].djiDestPath!!).toFile().readBytes())
    }

    @Test
    fun givenOriginUrlAvailableFromPeer_whenDownloadRuns_thenShouldDownloadFromPeer() {

    }

    fun givenMultiplePeersAvailable_whenDownloadRuns_thenShouldDownloadFromPeerWithMostFiles() {

    }
    
}