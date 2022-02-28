package com.ustadmobile.retriever.responder

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.ext.writeToFile
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File

class TestSingleItemResponder {

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    lateinit var catPicFile: File

    lateinit var db: RetrieverDatabase

    lateinit var mockUriResource: RouterNanoHTTPD.UriResource

    @Before
    fun setup() {
        catPicFile = temporaryFolder.newFile()
        this::class.java.getResourceAsStream("/cat-pic0.jpg").writeToFile(catPicFile)
        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }
        mockUriResource = mock<RouterNanoHTTPD.UriResource> {
            on { initParameter(RetrieverDatabase::class.java) }.thenReturn(db)
        }
    }

    @Test
    fun givenOriginUrlAvailable_whenGetCalled_thenShouldReturnDataMatchingOriginal() {
        db.locallyStoredFileDao.insert(LocallyStoredFile("http://cats.com/cat-pic0.jpg",
            catPicFile.absolutePath))

        val singleItemResponder = SingleItemResponder()

        val mockSession = mock<NanoHTTPD.IHTTPSession> {
            on { parameters }.thenReturn(mapOf("originUrl" to listOf("http://cats.com/cat-pic0.jpg")))
            on { uri }.thenReturn("/retriever/singular")
        }

        val getResponse = singleItemResponder.get(mockUriResource, mutableMapOf(), mockSession)
        val responseByteData = getResponse.data.readBytes()
        Assert.assertArrayEquals("Response byte data is the same as original file",
            catPicFile.readBytes(), responseByteData)
    }

    @Test
    fun givenOriginUrlNotAvailable_whenGetCalled_thenShouldReturn404Status() {
        val singleItemResponder = SingleItemResponder()

        val mockSession = mock<NanoHTTPD.IHTTPSession> {
            on { parameters }.thenReturn(mapOf("originUrl" to listOf("http://cats.com/cat-pic0.jpg")))
            on { uri }.thenReturn("/retriever/singular")
        }

        val getResponse = singleItemResponder.get(mockUriResource, mutableMapOf(), mockSession)

        Assert.assertEquals("Response code is 404 when file is not available",
            NanoHTTPD.Response.Status.NOT_FOUND, getResponse.status)
    }

}