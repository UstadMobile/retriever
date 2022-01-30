package com.ustadmobile.retriever.util


/**
 * Represents the parameters required for serving a partial response
 */
data class RangeResponse(
    /**
     * The response status code : 206 (if the request is valid), 416 (if range is unsatisfiable),
     * 400 (if request is invalid)
     */
    val statusCode: Int,

    /**
     * The first byte to serve (inclusive)
     */
    val fromByte: Long,

    /**
     * The last byte to serve (inclusive)
     */
    val toByte: Long,

    /**
     * The actual length of the range that will be served
     */
    val actualContentLength: Long,

    /**
     * The headers that should be added to the response (if statusCode = 206)
     */
    val responseHeaders: Map<String, String>
)