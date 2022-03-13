package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.ext.toDoorUri
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.requirePrefix
import com.ustadmobile.retriever.ext.url
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class DownloaderIntegrationTest {

    lateinit var originHttpServer: RouterNanoHTTPD

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var downloadDestDir: File

    lateinit var db: RetrieverDatabase

    @Before
    fun setup() {
        originHttpServer = RouterNanoHTTPD(0)
        originHttpServer.addRoute("/resources/.*", ResourcesResponder::class.java, "/resources")
        originHttpServer.start()
        downloadDestDir = temporaryFolder.newFile("downloads")
        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }
    }

    @Test
    fun givenOriginUrlsNotAvailableFromPeers_whenDownloadRuns_thenShouldDownloadFromOriginServer() {
        val batchId = systemTimeInMillis()

        val itemsToDownload = listOf(DownloadJobItem().apply {
                djiBatchId = batchId
                djiOriginUrl = originHttpServer.url("/resources/cat-pic0.jpg")
                djiDestPath = File(downloadDestDir, "cat-pic0.jpg").toDoorUri().toString()
            },
            DownloadJobItem().apply {
                djiBatchId = batchId
                djiOriginUrl = originHttpServer.url("/resources/pigeon1.png")
                djiDestPath = File(downloadDestDir, "pigeon1.png").toDoorUri().toString()
            }
        )

        runBlocking {
            db.downloadJobItemDao.insertList(itemsToDownload)
        }

        val mockAvailabilityManager = mock<AvailabilityManager>()

        val downloader = Downloader(batchId, mockAvailabilityManager, { }, mock { }, db)

        runBlocking {
            downloader.download()
        }

        Assert.assertArrayEquals(this::class.java.getResourceAsStream("/resources/cat-pic0.jpg")!!.readAllBytes(),
            File(downloadDestDir, "cat-pic0.jpg").readBytes())
        Assert.assertArrayEquals(this::class.java.getResourceAsStream("/resources/pigeon1.png")!!.readAllBytes(),
            File(downloadDestDir, "pigeon1.jpg").readBytes())
    }

    @Test
    fun givenOriginUrlAvailableFromPeer_whenDownloadRuns_thenShouldDownloadFromPeer() {

    }

    fun givenMultiplePeersAvailable_whenDownloadRuns_thenShouldDownloadFromPeerWithMostFiles() {

    }
    
}