package com.ustadmobile.retriever.responder

import com.google.gson.Gson
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.db.RetrieverDatabase
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.router.RouterNanoHTTPD
import org.junit.*
import org.mockito.kotlin.mock
import com.google.gson.reflect.TypeToken
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.FileAvailableResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.any


class TestAvailabilityResponder {

    private lateinit var db: RetrieverDatabase

    private lateinit var context: Any

    private lateinit var json: Json

    private val availableFiles: List<LocallyStoredFile> = (0..7).map {
        LocallyStoredFile("http://path.to/file$it", "/storage/file$it", 42, 0)
    }

    @Before
    fun setup(){
        context = Any()
        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb").build()
        db.clearAllTables()

        db.locallyStoredFileDao.insertList(availableFiles)
        json = Json { encodeDefaults = true }
    }


    @Suppress("UNCHECKED_CAST")
    private fun makeMockUriSession(urlsToRetrieve: List<String>) : NanoHTTPD.IHTTPSession {
        return mock {
            on { uri }.thenReturn("/retriever/")
            on { parseBody(any()) }.thenAnswer { invocation ->
                val map = invocation.arguments[0] as MutableMap<String, String>
                val filesJson = JsonArray(urlsToRetrieve.map { JsonPrimitive(it) })
                map["postData"] = Json.encodeToString(JsonArray.serializer(), filesJson)
                Unit
            }
            on { queryParameterString }.thenAnswer{
                val filesJson = JsonArray(urlsToRetrieve.map { JsonPrimitive(it) })
                Json.encodeToString(JsonArray.serializer(), filesJson)
            }
            on {method}.thenReturn(NanoHTTPD.Method.POST)
        }
    }

    @Test
    fun givenRequestResponder_whenPostRequestMade_thenShouldReturnResponse(){

        val responder = AvailabilityResponder()

        val mockUriResource: RouterNanoHTTPD.UriResource = mock {
            on { initParameter(RetrieverDatabase::class.java) }
                .thenReturn(db)
            on { initParameter(Json::class.java)}.thenReturn(json)
        }

        //0-7 are available, remainder are not
        val urlsToQuery = (0..9).map { "http://path.to/file$it" }

        val mockUriSession = makeMockUriSession(urlsToQuery)
        val response = responder.post(mockUriResource, mutableMapOf(), mockUriSession)

        Assert.assertEquals("Response status is 200 OK", NanoHTTPD.Response.Status.OK,
            response.status)

        val responseStr = String(response.data.readBytes())
        val responseEntryList = Gson().fromJson<List<FileAvailableResponse>>(
            responseStr, object: TypeToken<List<FileAvailableResponse>>(){ }.type)

        Assert.assertEquals(
            "Response list has same number of entries as those that are available",
            availableFiles.size, responseEntryList.size)

        availableFiles.forEach { localFile ->
            Assert.assertTrue(responseEntryList.any { it.originUrl == localFile.lsfOriginUrl })
        }

    }


}