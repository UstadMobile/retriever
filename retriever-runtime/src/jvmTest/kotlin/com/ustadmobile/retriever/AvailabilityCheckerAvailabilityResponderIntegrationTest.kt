package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.db.entities.AvailabilityResponse
import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import com.ustadmobile.retriever.ext.url
import com.ustadmobile.retriever.responder.AvailabilityResponder
import fi.iki.elonen.router.RouterNanoHTTPD
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AvailabilityCheckerAvailabilityResponderIntegrationTest {

    private lateinit var routerNanoHTTPD: RouterNanoHTTPD

    private lateinit var responderDb: RetrieverDatabase

    private lateinit var json: Json

    private lateinit var httpClient: HttpClient

    private val availableFiles: List<LocallyStoredFile> = (0..7).map {
        LocallyStoredFile("http://path.to/file$it", "/storage/file$it", 42, 0, "abc", "abc", "abc")
    }


    @Before
    fun setup() {
        responderDb = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "jvmTestDb")
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build()
        responderDb.clearAllTables()

        responderDb.locallyStoredFileDao.insertList(availableFiles)
        json = Json { encodeDefaults = true }

        routerNanoHTTPD = RouterNanoHTTPD(0)
        routerNanoHTTPD.addRoute("/availability", AvailabilityResponder::class.java,
            responderDb, json)
        routerNanoHTTPD.start()

        httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation)
            install(HttpTimeout)
        }

    }

    @Test
    fun givenListOfUrls_whenCheckersRequests_thenShouldParse() {
        val availabilityChecker = AvailabilityCheckerHttp(httpClient, json)

        val filesToRequest = (0..10).map { "http://path.to/file$it" }

        val response = runBlocking {
            availabilityChecker.checkAvailability(NetworkNode().apply {
                networkNodeId = 42
                networkNodeEndpointUrl = routerNanoHTTPD.url("/")
            }, filesToRequest)
        }

        Assert.assertEquals("Response has same number of available files as expected",
            availableFiles.size, response.results.size)

        availableFiles.forEach {
            Assert.assertTrue("Response has ${it.lsfOriginUrl}", response.hasOriginUrl(it.lsfOriginUrl!!))
        }
    }
}