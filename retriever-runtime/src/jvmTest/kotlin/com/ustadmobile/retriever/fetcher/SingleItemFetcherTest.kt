package com.ustadmobile.retriever.fetcher

import com.ustadmobile.door.ext.toDoorUri
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.ResourcesResponder
import com.ustadmobile.retriever.ext.url
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File
import java.io.IOException

class SingleItemFetcherTest {

    private lateinit var originHttpServer: RouterNanoHTTPD

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var downloadDestDir: File

    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setup() {
        originHttpServer = RouterNanoHTTPD(0)
        originHttpServer.addRoute("/resources/.*", ResourcesResponder::class.java,
            "/resources")
        originHttpServer.start()
        okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()
        downloadDestDir = temporaryFolder.newFolder()
    }

    @After
    fun tearDown() {

    }

    @Test
    fun givenValidUrl_whenDownloadCalled_thenShouldDownloadToDestination() {
        val destFile = File(downloadDestDir, "cat-pic0")
        val mockProgressListener = mock<FetchProgressListener>()

        runBlocking {
            SingleItemFetcher(okHttpClient).download(
                DownloadJobItem().apply {
                    djiOriginUrl = originHttpServer.url("/resources/cat-pic0.jpg")
                    djiDestPath = destFile.absolutePath
                } , mockProgressListener)
        }

        Assert.assertArrayEquals("Downloaded bytes match original bytes",
            this::class.java.getResource("/cat-pic0.jpg")!!.readBytes(),
            destFile.readBytes())

        verify(mockProgressListener).onFetchProgress(argWhere {
            it.bytesSoFar == 0L && it.totalBytes > 0L
        })
        verify(mockProgressListener).onFetchProgress(argWhere {
            it.bytesSoFar > 0L && it.bytesSoFar == it.totalBytes
        })
    }

    @Test(expected = IOException::class)
    fun givenUrlDoesntExist_whenDownloadCalled_thenShouldFail() {
        val destFile = File(downloadDestDir, "cat-pic0")
        runBlocking {
            SingleItemFetcher(okHttpClient).download(DownloadJobItem().apply {
                djiOriginUrl = originHttpServer.url("/doesnotexist.jpg")
                djiDestPath = destFile.absolutePath
            }, { })
        }
    }

    @Test
    fun givenPartialDownloadExists_whenDownloadCalled_thenShouldResume() {
        val destFile = File(downloadDestDir, "cat-pic0")

        val bytesInItem = this::class.java.getResourceAsStream("/cat-pic0.jpg").readBytes()
        val partialBytes = bytesInItem.copyOf(bytesInItem.size / 2)
        destFile.writeBytes(partialBytes)

        val mockProgressListener = mock<FetchProgressListener>()

        runBlocking {
            SingleItemFetcher(okHttpClient).download(
                DownloadJobItem().apply {
                    djiOriginUrl = originHttpServer.url("/resources/cat-pic0.jpg")
                    djiDestPath = destFile.absolutePath
                } , mockProgressListener)
        }

        Assert.assertArrayEquals("Downloaded bytes match original bytes",
            this::class.java.getResource("/cat-pic0.jpg")!!.readBytes(),
            destFile.readBytes())
    }

}