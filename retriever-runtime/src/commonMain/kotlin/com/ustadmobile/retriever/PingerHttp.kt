package com.ustadmobile.retriever

import com.ustadmobile.retriever.ext.requirePostfix
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*

/**
 * Simple implementation of the pinger
 */
class PingerHttp(
    private val httpClient: HttpClient,
    private val pingTimeout: Long = 5000,
): Pinger {

    override suspend fun ping(endpoint: String, localListeningPort: Int) {
        httpClient.get<Unit>(endpoint.requirePostfix("/") + "ping") {
            header(RetrieverCommon.RETRIEVER_PORT_HEADER, localListeningPort.toString())
            timeout {
                requestTimeoutMillis = pingTimeout
            }
        }
    }

}