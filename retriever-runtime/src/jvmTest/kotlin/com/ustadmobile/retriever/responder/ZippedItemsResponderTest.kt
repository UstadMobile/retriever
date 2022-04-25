package com.ustadmobile.retriever.responder

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.ext.writeToFile
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import com.ustadmobile.retriever.ext.asLocallyStoredFile
import com.ustadmobile.retriever.ext.crc32
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 *
 */
class ZippedItemsResponderTest {

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    lateinit var catPicFile: File

    lateinit var overlayFile: File

    lateinit var db: RetrieverDatabase

    lateinit var mockUriResource: RouterNanoHTTPD.UriResource

    @Before
    fun setup() {
        catPicFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/cat-pic0.jpg")!!.writeToFile(catPicFile)

        overlayFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/animated-overlay.gif")!!.writeToFile(overlayFile)

        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build().also {
                it.clearAllTables()
            }
        db.locallyStoredFileDao.insertList(listOf(
            catPicFile.asLocallyStoredFile("http://cats.com/cat-pic0.jpg"),
            overlayFile.asLocallyStoredFile("http://cats.com/overlay.gif")))


        mockUriResource = mock {
            on { initParameter(RetrieverDatabase::class.java) }.thenReturn(db)
        }
    }


    @Suppress("UNCHECKED_CAST", "RedundantUnitExpression")
    private fun makeMockUriSession(urlsToRetrieve: List<String>) : NanoHTTPD.IHTTPSession {
        return mock {
            on { uri }.thenReturn("/retriever/")
            on { parseBody(any()) }.thenAnswer { invocation ->
                val map = invocation.arguments[0] as MutableMap<String, String>
                val filesJson = JsonArray(urlsToRetrieve.map { JsonPrimitive(it) })
                map["postData"] = Json.encodeToString(JsonArray.serializer(), filesJson)
                Unit
            }
            on {method}.thenReturn(NanoHTTPD.Method.POST)
        }
    }

    @Test
    fun givenValidRequestWhereAllFilesAvailable_whenPostCalled_thenShouldReturnZipInOrder() {
        val responder = ZippedItemsResponder()

        val urlsToRetrieve = listOf("http://cats.com/cat-pic0.jpg", "http://cats.com/overlay.gif")
        val mockUriSession = makeMockUriSession(urlsToRetrieve)

        val response = responder.post(mockUriResource, mutableMapOf(), mockUriSession)

        Assert.assertEquals("Response status is 200 OK", NanoHTTPD.Response.Status.OK,
            response.status)

        val responseBytes = response.data.readBytes()
        val zipIn = ZipInputStream(ByteArrayInputStream(responseBytes))
        val entry1 = zipIn.nextEntry!!
        val entry1Data = zipIn.readBytes()
        val entry2 = zipIn.nextEntry!!
        val entry2Data =  zipIn.readBytes()
        zipIn.close()

        Assert.assertArrayEquals("First entry data is equal to original content",
            catPicFile.readBytes(), entry1Data)
        Assert.assertEquals("Name for entry1 is the origin url", "http://cats.com/cat-pic0.jpg",
            entry1.name)
        Assert.assertArrayEquals("Second entry data is equal to original content",
            overlayFile.readBytes(), entry2Data)
        Assert.assertEquals("Name for entry2 is the origin url", "http://cats.com/overlay.gif",
            entry2.name)

        Assert.assertEquals("Content-length header matches actual length",
            responseBytes.size, response.getHeader("Content-Length").toInt())
    }

    @Test
    fun givenInvalidRequestWithAFileNotAvailable_whenPostCalled_thenShouldRespondBadRequest() {
        val responder = ZippedItemsResponder()

        val urlsToRetrieve = listOf("http://cats.com/cat-pic0.jpg", "http://othersite.com/otherfile.gif")

        val mockUriSession = makeMockUriSession(urlsToRetrieve)

        val response = responder.post(mockUriResource, mutableMapOf(), mockUriSession)

        Assert.assertEquals("Invalid request where one file is not available returns bad request",
            NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
    }

    @Test
    fun givenInvalidRequestWithNoFilesRequested_whenPostCalled_thenShouldRespondBadRequest() {
        val responder = ZippedItemsResponder()

        val urlsToRetrieve = listOf<String>()

        val mockUriSession = makeMockUriSession(urlsToRetrieve)

        val response = responder.post(mockUriResource, mutableMapOf(), mockUriSession)

        Assert.assertEquals("Invalid request where one file is not available returns bad request",
            NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
    }

}