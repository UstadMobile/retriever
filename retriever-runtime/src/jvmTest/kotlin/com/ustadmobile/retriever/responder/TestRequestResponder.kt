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
import com.ustadmobile.lib.db.entities.LocallyStoredFile


class TestRequestResponder {

    private lateinit var db: RetrieverDatabase
    private lateinit var context: Any

    private val availableFilesToInsert: List<LocallyStoredFile> = listOf(
        LocallyStoredFile("http://path.to/file1", "http://local.path.to/file1", 0 , 0),
        LocallyStoredFile("http://path.to/file2", "http://local.path.to/file2", 0 , 0),
        LocallyStoredFile("http://path.to/file3", "http://local.path.to/file3", 0 , 0),
        LocallyStoredFile("http://path.to/file4", "http://local.path.to/file4", 0 , 0),
        LocallyStoredFile("http://path.to/file5", "http://local.path.to/file5", 0 , 0),

    )


    @Before
    fun setup(){
        context = Any()
        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb").build()
        db.clearAllTables()

        //Add Available files
        if(db.locallyStoredFileDao.findAllAvailableFiles().isEmpty()){
            db.locallyStoredFileDao.insertList(availableFilesToInsert)
        }

    }

    /**
     * TODO: Split this into two separate tests
     */
    @Test
    fun givenRequestResponder_whenGetRequestMade_thenShouldReturnResponse(){
        val responder = RequestResponder()

        val mockUriResource = mock<RouterNanoHTTPD.UriResource> {
            on {initParameter(PARAM_DB_INDEX, RetrieverDatabase::class.java)}.thenReturn(db)
        }

        val mockSessionAvailable = mock<NanoHTTPD.IHTTPSession>{
            on { parameters }.thenReturn(
                mutableMapOf(PARAM_FILE_REQUEST_URL to listOf("http://path.to/file1"))
            )
        }

        val mockSessionUnavailable = mock<NanoHTTPD.IHTTPSession>{
            on { parameters }.thenReturn(
                mutableMapOf(PARAM_FILE_REQUEST_URL to listOf("http://path.to/file42"))
            )
        }

        val response = responder.get(mockUriResource, mutableMapOf(), mockSessionAvailable)

        Assert.assertNotNull("Response is not null", response)

        val responseStr = String(response.data.readBytes())
        val responseEntryList = Gson().fromJson<List<LocallyStoredFile>>(
                responseStr,
                object: TypeToken<List<LocallyStoredFile>>(){

                }.type
            )
        Assert.assertEquals("Node has file", 1, responseEntryList.size)

        val responseUnavailable = responder.get(mockUriResource, mutableMapOf(), mockSessionUnavailable)

        Assert.assertNotNull("Response is not null", responseUnavailable)

        val responseStrUnavailable = String(responseUnavailable.data.readBytes())
        val responseEntryListUnavailable = Gson().fromJson<List<LocallyStoredFile>>(
            responseStrUnavailable,
            object: TypeToken<List<LocallyStoredFile>>(){

            }.type
        )
        Assert.assertEquals("Node has not found the file", 0, responseEntryListUnavailable.size)

    }

}