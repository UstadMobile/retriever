package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.ext.requirePostfix
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AvailabilityCheckerHttp(
    private val httpClient: HttpClient,
    private val json: Json,
) : AvailabilityChecker {

    override suspend fun checkAvailability(
        networkNode: NetworkNode,
        originUrls: List<String>
    ): AvailabilityCheckerResult {
        val url = "${networkNode.networkNodeEndpointUrl?.requirePostfix("/")}availability"

        val originUrlsJsonStr = json.encodeToString(ListSerializer(String.serializer()), originUrls)
        val result: List<FileAvailableResponse> = httpClient.post(url) {
            body = TextContent(originUrlsJsonStr, contentType = ContentType.Application.Json)
        }

        return AvailabilityCheckerResult(result, networkNode.networkNodeId)
    }

}