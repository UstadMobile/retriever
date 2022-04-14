package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.NetworkNode
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AvailabilityCheckerHttpTest {


    private lateinit var mockWebServer: MockWebServer

    private lateinit var json: Json

    private lateinit var httpClient: HttpClient


    @Before
    fun setup() {
        json = Json { encodeDefaults = true }
        httpClient = HttpClient(OkHttp) {
            install(JsonFeature)
            install(HttpTimeout)
        }

        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @Test
    fun givenServerRunning_whenCheckAvailabilityCalled_thenWillReturnExpectedResult() {
        val checker = AvailabilityCheckerHttp(httpClient, json)

        val availableResponse = TEST_URLS.filter { AVAILABILITY.containsKey(it) }
            .map { FileAvailableResponse(it, "aa", 42) }

        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(ListSerializer(FileAvailableResponse.serializer()), availableResponse))
            .setHeader("content-type", "application/json"))

        val result = runBlocking {
            checker.checkAvailability(NetworkNode().apply {
                networkNodeEndpointUrl = mockWebServer.url("/").toString()
                networkNodeId = 1
            }, TEST_URLS)
        }

        Assert.assertEquals("Response has the same number in list as availability data",
            AVAILABILITY.size, result.results.size)
        AVAILABILITY.forEach { availEntry ->
            Assert.assertTrue("Available file is in response decoded", result.results.any {
                it.originUrl ==  availEntry.key
            })
        }

    }

    fun givenServerDown_whenCheckAvailabilityCalled_thenWillThrowException() {

    }

    companion object {

        val TEST_URLS = (1..5).map {
            "https://server.com/file$it.zip"
        }

        val AVAILABILITY = TEST_URLS.mapIndexed {index, url ->
            url to ((index % 2) == 0)
        }.toMap()

    }


}