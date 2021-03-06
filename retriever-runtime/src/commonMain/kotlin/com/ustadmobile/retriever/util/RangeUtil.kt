package com.ustadmobile.retriever.util

val RANGE_CONTENT_LENGTH_HEADER = "Content-Length"
val RANGE_CONTENT_RANGE_HEADER = "Content-Range"
val RANGE_CONTENT_ACCEPT_RANGE_HEADER = "Accept-Ranges"


fun parseRangeRequestHeader(rangeHeader: String, totalLength: Long): RangeResponse {

    var fromByte = -1L
    var toByte = -1L
    var statusCode = 0
    var responseHeaders = emptyMap<String, String>()
    var actualContentLength = 0L

    try {
        val header = rangeHeader.substring("bytes=".length)

        val dashPos = header.indexOf('-')
        if (dashPos > 0) {
            fromByte = header.substring(0, dashPos).toLong()
        }

        if (dashPos == header.length - 1) {
            toByte = totalLength - 1
        } else if (dashPos > 0) {
            toByte = header.substring(dashPos + 1).toLong()
        }

        if(fromByte == -1L || toByte == -1L) {
            statusCode = 400
        }else if(fromByte >= 0 && fromByte < totalLength
            && toByte > 0 && toByte <= totalLength) {
            /*
             * range request is inclusive: e.g. range 0-1 length is 2 bytes as per
             * https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html 14.35.1 Byte Ranges
             */
            actualContentLength = (toByte + 1) - fromByte
            responseHeaders = mapOf(
                RANGE_CONTENT_LENGTH_HEADER to actualContentLength.toString(),
                RANGE_CONTENT_RANGE_HEADER to "bytes $fromByte-$toByte/$totalLength",
                RANGE_CONTENT_ACCEPT_RANGE_HEADER to "bytes"
            )

            statusCode = 206
        }else {
            statusCode = 416
        }
    } catch (e: Exception) {
        statusCode = 400
    }

    return RangeResponse(statusCode, fromByte, toByte, actualContentLength,
        responseHeaders)
}


