package com.ustadmobile.retriever.responder

import com.google.gson.Gson
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.db.RetrieverDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import org.junit.*
import org.mockito.kotlin.mock
import com.ustadmobile.retriever.responder.RequestResponder.Companion.PARAM_FILE_REQUEST_URL
import com.ustadmobile.retriever.responder.RequestResponder.Companion.PARAM_DB_INDEX
import com.google.gson.reflect.TypeToken
import com.ustadmobile.lib.db.entities.AvailableFile


class TestRequestResponder {

    private lateinit var db: RetrieverDatabase
    private lateinit var context: Any

    private val availableFilesToInsert: List<AvailableFile> = listOf(
        AvailableFile("http://path.to/file1", "http://local.path.to/file1"),
        AvailableFile("http://path.to/file2", "http://local.path.to/file2"),
        AvailableFile("http://path.to/file3", "http://local.path.to/file3"),
        AvailableFile("http://path.to/file4", "http://local.path.to/file4"),
        AvailableFile("http://path.to/file5", "http://local.path.to/file5"),

    )


    @Before
    fun setup(){
        context = Any()
        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb").build()
        db.clearAllTables()

        //Add Available files
        if(db.availableFileDao.findAllAvailableFiles().isEmpty()){
            db.availableFileDao.insertList(availableFilesToInsert)
        }

    }

    @Test
    fun test_Test(){
        Assert.assertEquals(1,1)
    }

    @Test
    fun givenRequestResponder_whenGetRequestMade_thenShouldReturnResponse(){
        val responder = RequestResponder()

        val mockUriResource = mock<RouterNanoHTTPD.UriResource> {
            on {initParameter(PARAM_DB_INDEX, RetrieverDatabase::class.java)}.thenReturn(db)
        }

        val mockSession = mock<NanoHTTPD.IHTTPSession>{
            on { parameters }.thenReturn(
                mutableMapOf(PARAM_FILE_REQUEST_URL to listOf("http://path.to/file1"))
            )
        }

        val response = responder.get(mockUriResource, mutableMapOf(), mockSession)

        Assert.assertNotNull("Response is not null", response)

        val responseStr = String(response.data.readBytes())
        val responseEntryList = Gson().fromJson<List<AvailableFile>>(
                responseStr,
                object: TypeToken<List<AvailableFile>>(){

                }.type
            )
        Assert.assertEquals("Node has file", 1, responseEntryList.size)

    }

}