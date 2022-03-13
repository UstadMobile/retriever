package com.ustadmobile.retriever.fetcher

import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.toDoorUri
import com.ustadmobile.door.ext.toFile
import com.ustadmobile.lib.db.entities.DownloadJobItem
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
import org.mockito.kotlin.mock
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.ustadmobile.retriever.ext.headerSize
import com.ustadmobile.retriever.io.RangeInputStream
import java.io.*
import java.util.zip.ZipInputStream

class MultiItemFetcherTest {

    private lateinit var peerHttpServer: RouterNanoHTTPD

    private lateinit var downloadDestDir: File

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var tmpZipFile: File

    private lateinit var mockFetchProgressListener: FetchProgressListener

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

    @Before
    fun setup() {
        tmpZipFile = temporaryFolder.newFile()
        val zout = ZipOutputStream(FileOutputStream(tmpZipFile))
        zout.setMethod(ZipOutputStream.STORED)
        val crc32 = CRC32()
        RESOURCE_PATH_LIST.forEach { resPath ->
            val entryBytes = this::class.java.getResourceAsStream(resPath)!!.readAllBytes()
            crc32.update(entryBytes)
            zout.putNextEntry(ZipEntry("$originUrlPrefix$resPath").apply {
                crc = crc32.value
                compressedSize = entryBytes.size.toLong()
                size = entryBytes.size.toLong()
            })
            zout.write(entryBytes)
            crc32.reset()
            zout.closeEntry()
        }
        zout.flush()
        zout.close()

        peerHttpServer = RouterNanoHTTPD(0)
        peerHttpServer.start()

        okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()

        downloadDestDir = temporaryFolder.newFolder()

        mockFetchProgressListener = mock { }

        downloadJobItems = RESOURCE_PATH_LIST.mapIndexed { index, resPath ->
            DownloadJobItem().apply {
                djiOriginUrl = "$originUrlPrefix$resPath"
                djiDestPath = File(downloadDestDir, resPath.substringAfterLast("/")).toDoorUri().toString()
                djiIndex = index
            }
        }
    }

    @Test
    fun givenHostWithFiles_whenDownloadCalled_thenShouldDownloadToDestination()  {
        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, tmpZipFile)
        val hostEndpoint = peerHttpServer.url("/retriever/")
        val multiItemFetcher = MultiItemFetcher(okHttpClient, json)

        runBlocking {
            multiItemFetcher.download(hostEndpoint, downloadJobItems, mockFetchProgressListener)
        }

        downloadJobItems.forEach {
            Assert.assertArrayEquals("Content for ${it.djiOriginUrl} is the same",
                this::class.java.getResourceAsStream("${it.djiOriginUrl?.removePrefix(originUrlPrefix)}")!!.readBytes(),
                DoorUri.parse(it.djiDestPath!!).toFile().readBytes())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun givenHostWithErrorResponse_whenDownloadCalled_thenShouldThrowIllegalStateException() {
        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, File("/does/not/exist"))
        val hostEndpoint = peerHttpServer.url("/retriever/")
        val multiItemFetcher = MultiItemFetcher(okHttpClient, json)

        runBlocking {
            multiItemFetcher.download(hostEndpoint, downloadJobItems, mockFetchProgressListener)
        }
    }

    @Test
    fun givenPartialDownload_whenDownloadCalled_thenShouldResume() {
        peerHttpServer.addRoute("/retriever/zipped", PostFileResponder::class.java, tmpZipFile)
        val hostEndpoint = peerHttpServer.url("/retriever/")

        val resource1Bytes = this::class.java.getResourceAsStream(RESOURCE_PATH_LIST[0])!!.readBytes()
        DoorUri.parse(downloadJobItems[0].djiDestPath!!).toFile()
            .writeBytes(resource1Bytes.copyOf(resource1Bytes.size / 2))

        val multiItemFetcher = MultiItemFetcher(okHttpClient, json)

        val fetchResult = runBlocking {
            multiItemFetcher.download(hostEndpoint, downloadJobItems, mockFetchProgressListener)
        }



        downloadJobItems.forEach {
            Assert.assertArrayEquals("Content for ${it.djiOriginUrl} is the same",
                this::class.java.getResourceAsStream("${it.djiOriginUrl?.removePrefix(originUrlPrefix)}")!!.readBytes(),
                DoorUri.parse(it.djiDestPath!!).toFile().readBytes())
        }
    }

    @Test
    fun testZipSplit() {
        val zipEntry1 = ZipEntry(downloadJobItems.first().djiOriginUrl!!)
        val firstHeaderBytes = tmpZipFile.readBytes().copyOf(zipEntry1.headerSize)
        val partFirstFileBytes = this::class.java.getResourceAsStream(RESOURCE_PATH_LIST.first()).readBytes()
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
        lateinit var entry: ZipEntry
        while (zipIn.nextEntry?.also { entry = it } != null) {
            val entryBytes = zipIn.readAllBytes()
            val resourceBytes = this::class.java.getResourceAsStream(RESOURCE_PATH_LIST[entryCount])!!.readAllBytes()
            Assert.assertArrayEquals(resourceBytes, entryBytes)
            entryCount++
        }

    }



    companion object {

        val RESOURCE_PATH_LIST = listOf("/animated-overlay.gif", "/cat-pic0.jpg", "/pigeon1.png")

        const val originUrlPrefix = "http://www.server.com"
    }

}