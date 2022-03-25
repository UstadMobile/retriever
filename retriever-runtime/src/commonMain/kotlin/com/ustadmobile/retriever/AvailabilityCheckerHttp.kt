package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.ext.requirePostfix
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AvailabilityCheckerHttp(
    private val httpClient: HttpClient,
) : AvailabilityChecker {

    override suspend fun checkAvailability(
        networkNode: NetworkNode,
        originUrls: List<String>
    ): AvailabilityCheckerResult {
        val url = "${networkNode.networkNodeEndpointUrl?.requirePostfix("/")}availability"
        val result = httpClient.post<List<FileAvailableResponse>>(url) {
            contentType(ContentType.Application.Json)
            body = originUrls
        }

        return AvailabilityCheckerResult(result, networkNode.networkNodeId)
    }

}