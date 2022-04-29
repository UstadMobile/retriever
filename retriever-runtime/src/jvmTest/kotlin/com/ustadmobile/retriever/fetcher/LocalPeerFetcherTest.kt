package com.ustadmobile.retriever.fetcher

import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.ext.headerSize
import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.responder.AbstractUriResponder
import com.ustadmobile.retriever.responder.FileResponder
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.ustadmobile.retriever.io.RangeInputStream
import com.ustadmobile.retriever.util.assertSuccessfullyCompleted
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.mockito.kotlin.*
import java.io.*
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipInputStream

class LocalPeerFetcherTest {

    private lateinit var peerHttpServer: RouterNanoHTTPD

    private lateinit var downloadDestDir: File

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var tmpZipFile: File

    private lateinit var mockRetrieverListener: RetrieverListener

    private lateinit var integrityMap: MutableMap<String, String>

    private val json = Json {
        encodeDefaults = true
    }

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var downloadJobItems: List<DownloadJobItem>

    class PostFileResponder(): AbstractUriResponder() {
        override fun post(
            uriResource: RouterNanoHTTPD.UriResource,
            urlParams: MutableMap<String, String>,
            session: NanoHTTPD.IHTTPSession
        ): NanoHTTPD.Response {
            val responseFile = uriResource.initParameter(File::class.java)
            return FileResponder.newResponseFromFile(uriResource, session, FileResponder.FileSource(responseFile)).also {
                it.addHeader("connection", "close")
            }
        }
    }

    fun makeTempZipFile(
        byteReader: (index: Int, path: String) -> ByteArray = { _, path ->
            this::class.java.getResourceAsStream(path)!!.readAllBytes()
        }
    ) {
        val zout = ZipOutputStream(FileOutputStream(tmpZipFile))
        zout.setMethod(ZipOutputStream.STORED)
        val crc32 = CRC32()
        val messageDigest = MessageDigest.getInstance("SHA-384")
        RESOURCE_PATH_LIST.forEachIndexed { index, resPath ->
            val entryBytes = byteReader(index, resPath)
            crc32.update(entryBytes)
            messageDigest.update(entryBytes)
            integrityMap[resPath] = "sha384-" + Base64.getEncoder().encodeToString(messageDigest.digest())
            zout.putNextEntry(ZipEntry("$originUrlPrefix$resPath").apply {
                crc = crc32.value
                compressedSize = entryBytes.size.toLong()
                size = entryBytes.size.toLong()
            })
            zout.write(entryBytes)
            crc32.reset()
            messageDigest.reset()
            zout.closeEntry()
        }
        zout.flush()
        zout.close()
    }

    @Before
    fun setup() {
        tmpZipFile = temporaryFolder.newFile()
        integrityMap = mutableMapOf()
        makeTempZipFile()
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())

        peerHttpServer = RouterNanoHTTPD(0)
        peerHttpServer.start()


        okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()

        downloadDestDir = temporaryFolder.newFolder()

        mockRetrieverListener = mock { }

        downloadJobItems = RESOURCE_PATH_LIST.mapIndexed { index, resPath ->
            DownloadJobItem().apply {
                djiOriginUrl = "$originUrlPrefix$resPath"
                djiDestPath = File(downloadDestDir, resPath.substringAfterLast("/")).absolutePath
                djiIndex = index
                djiIntegrity = integrityMap[resPath]
                djiUid = index
            }
        }
    }

    /**
     * Check each download item completed successfully as expected, including
     *  1. The bytes stored in the destination file match with the original bytes
     *  2. An event was provided to the retrieverListener that the download was successful
     *  3. The success event contains crc32 and SHA-256 that matches the expected values
     */
    private fun assertDownloadJobItemsSuccessfullyCompleted() {
        downloadJobItems.forEach {
            it.assertSuccessfullyCompleted(mockRetrieverListener, originUrlPrefix)
        }
    }

    @Test
    fun givenHostWithFiles_whenDownloadCalled_thenShouldDownloadToDestination()  {
        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, tmpZipFile)
        val hostEndpoint = peerHttpServer.url("/retriever/")
        val localPeerFetcher = LocalPeerFetcher(okHttpClient, json)

        runBlocking {
            localPeerFetcher.download(hostEndpoint, downloadJobItems, mockRetrieverListener)
        }

        assertDownloadJobItemsSuccessfullyCompleted()
    }

    @Test(expected = IllegalStateException::class)
    fun givenHostWithErrorResponse_whenDownloadCalled_thenShouldThrowIllegalStateException() {
        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, File("/does/not/exist"))
        val hostEndpoint = peerHttpServer.url("/retriever/")
        val localPeerFetcher = LocalPeerFetcher(okHttpClient, json)

        runBlocking {
            localPeerFetcher.download(hostEndpoint, downloadJobItems, mockRetrieverListener)
        }
    }

    @Test
    fun givenPartialDownload_whenDownloadCalled_thenShouldResume() {
        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, tmpZipFile)
        val hostEndpoint = peerHttpServer.url("/retriever/")

        val resource1Bytes = this::class.java.getResourceAsStream(RESOURCE_PATH_LIST[0])!!.readBytes()
        File(downloadJobItems[0].djiDestPath!!).writeBytes(resource1Bytes.copyOf(resource1Bytes.size / 2))

        val localPeerFetcher = LocalPeerFetcher(okHttpClient, json)

        runBlocking {
            localPeerFetcher.download(hostEndpoint, downloadJobItems, mockRetrieverListener)
        }

        assertDownloadJobItemsSuccessfullyCompleted()
    }

    @Test
    fun testZipSplit() {
        val zipEntry1 = ZipEntry(downloadJobItems.first().djiOriginUrl!!)
        val firstHeaderBytes = tmpZipFile.readBytes().copyOf(zipEntry1.headerSize)
        val partFirstFileBytes = this::class.java.getResourceAsStream(RESOURCE_PATH_LIST.first())!!.readBytes()
        val partSize = partFirstFileBytes.size / 2

        val byteArrOut = ByteArrayOutputStream()
        byteArrOut.writeBytes(firstHeaderBytes)
        byteArrOut.writeBytes(partFirstFileBytes.copyOf(partSize))
        val offset = firstHeaderBytes.size + partSize

        val remainderByteArrOut = ByteArrayOutputStream()

        RangeInputStream(FileInputStream(tmpZipFile), offset.toLong(), tmpZipFile.length()).use { rangeIn ->
            rangeIn.copyTo(remainderByteArrOut)
        }

        remainderByteArrOut.flush()
        byteArrOut.writeBytes(remainderByteArrOut.toByteArray())

        byteArrOut.flush()
        val combined = byteArrOut.toByteArray()
        Assert.assertEquals("Files are same size", tmpZipFile.length(), combined.size.toLong())

        val zipIn = ZipInputStream(ByteArrayInputStream(combined))
        var entryCount = 0

        while (zipIn.nextEntry != null) {
            val entryBytes = zipIn.readAllBytes()
            val resourceBytes = this::class.java.getResourceAsStream(RESOURCE_PATH_LIST[entryCount])!!.readAllBytes()
            Assert.assertArrayEquals(resourceBytes, entryBytes)
            entryCount++
        }
    }


    @Test
    fun givenInvalidDataOnOneItem_whenDownloaded_thenItemAttemptShouldFailForFileWithInvalidDataOthersShouldSucceed() {
        val corruptResIndex = 0

        makeTempZipFile {index, resPath ->
            if(index == 0) {
                "Corrupt Data".toByteArray()
            }else {
                this::class.java.getResourceAsStream(resPath)!!.readAllBytes()
            }
        }

        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, tmpZipFile)


        val hostEndpoint = peerHttpServer.url("/retriever/")
        val localPeerFetcher = LocalPeerFetcher(okHttpClient, json)

        runBlocking {
            localPeerFetcher.download(hostEndpoint, downloadJobItems, mockRetrieverListener)
        }

        downloadJobItems.forEachIndexed { index, jobItem ->
            if(index == corruptResIndex) {
                verifyBlocking(mockRetrieverListener, atLeastOnce()) {
                    onRetrieverStatusUpdate(argWhere { evt ->
                        evt.downloadJobItemUid == jobItem.djiUid && evt.status == STATUS_ATTEMPT_FAILED
                    })
                }

                Assert.assertFalse("Corrupt download was deleted", File(jobItem.djiDestPath!!).exists())

            }else {
                jobItem.assertSuccessfullyCompleted(mockRetrieverListener, originUrlPrefix)
            }

        }
    }


    companion object {

        val RESOURCE_PATH_LIST = listOf("/animated-overlay.gif", "/cat-pic0.jpg", "/pigeon1.png")

        const val originUrlPrefix = "http://www.server.com"
    }

}