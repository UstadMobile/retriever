package com.ustadmobile.retriever.fetcher

import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.ResourcesResponder
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_SUCCESSFUL
import com.ustadmobile.retriever.ext.url
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import java.io.File
import java.security.MessageDigest
import java.util.*

class OriginServerFetcherTest {

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
        val mockProgressListener = mock<RetrieverListener>()

        runBlocking {
            val resourceBytes = this::class.java.getResourceAsStream("/cat-pic0.jpg")!!.readAllBytes()
            val messageDigest = MessageDigest.getInstance("SHA-384")
            messageDigest.update(resourceBytes)

            OriginServerFetcher(okHttpClient).download(
                listOf(DownloadJobItem().apply {
                    djiOriginUrl = originHttpServer.url("/resources/cat-pic0.jpg")
                    djiDestPath = destFile.absolutePath
                    djiIntegrity = "sha384-" + Base64.getEncoder().encodeToString(messageDigest.digest())
                }) , mockProgressListener)
        }

        Assert.assertArrayEquals("Downloaded bytes match original bytes",
            this::class.java.getResource("/cat-pic0.jpg")!!.readBytes(),
            destFile.readBytes())

        verifyBlocking (mockProgressListener) {
            onRetrieverProgress(argWhere {
                it.bytesSoFar == 0L && it.totalBytes > 0L
            })
        }

        verifyBlocking(mockProgressListener) {
            onRetrieverProgress(argWhere {
                it.bytesSoFar > 0L && it.bytesSoFar == it.totalBytes
            })

            onRetrieverStatusUpdate(argWhere {
                it.status == STATUS_SUCCESSFUL
            })
        }
    }

    @Test
    fun givenUrlDoesntExist_whenDownloadCalled_thenShouldFail() {
        val destFile = File(downloadDestDir, "cat-pic0")
        val mockProgressListener = mock<RetrieverListener>{ }
        runBlocking {
            OriginServerFetcher(okHttpClient).download(listOf(DownloadJobItem().apply {
                djiOriginUrl = originHttpServer.url("/doesnotexist.jpg")
                djiDestPath = destFile.absolutePath
            }), mockProgressListener)
        }

        verifyBlocking(mockProgressListener) {
            onRetrieverStatusUpdate(argWhere { it.status == STATUS_ATTEMPT_FAILED })
        }
    }

    @Test
    fun givenPartialDownloadExists_whenDownloadCalled_thenShouldResume() {
        val destFile = File(downloadDestDir, "cat-pic0")

        val bytesInItem = this::class.java.getResourceAsStream("/cat-pic0.jpg")!!.readBytes()
        val partialBytes = bytesInItem.copyOf(bytesInItem.size / 2)
        destFile.writeBytes(partialBytes)

        val mockProgressListener = mock<RetrieverListener>()

        runBlocking {
            OriginServerFetcher(okHttpClient).download(
                listOf(DownloadJobItem().apply {
                    djiOriginUrl = originHttpServer.url("/resources/cat-pic0.jpg")
                    djiDestPath = destFile.absolutePath
                }), mockProgressListener)
        }

        Assert.assertArrayEquals("Downloaded bytes match original bytes",
            this::class.java.getResource("/cat-pic0.jpg")!!.readBytes(),
            destFile.readBytes())
    }

    @Test
    fun givenValidUrl_whenIntegrityDoesNotMatch_thenAttemptShouldFail() {
        val destFile = File(downloadDestDir, "cat-pic0")
        val mockProgressListener = mock<RetrieverListener>()

        runBlocking {
            val resourceBytes = this::class.java.getResourceAsStream("/cat-pic0.jpg")!!.readAllBytes()
            val messageDigest = MessageDigest.getInstance("SHA-384")
            messageDigest.update(resourceBytes)

            OriginServerFetcher(okHttpClient).download(
                listOf(DownloadJobItem().apply {
                    djiOriginUrl = originHttpServer.url("/resources/animated-overlay.gif")
                    djiDestPath = destFile.absolutePath
                    djiIntegrity = "sha384-" + Base64.getEncoder().encodeToString(messageDigest.digest())
                }), mockProgressListener)
        }

        verifyBlocking(mockProgressListener) {
            onRetrieverStatusUpdate(argWhere {
                it.status == STATUS_ATTEMPT_FAILED
            })
        }

        Assert.assertFalse("If checksum does not match, file does not exist",
            destFile.exists())
    }


}