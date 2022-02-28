package com.ustadmobile.retriever.ext

import fi.iki.elonen.NanoHTTPD
import java.io.File

fun NanoHTTPD.IHTTPSession.receiveRequestBody() : String? {
    val bodyMap = mutableMapOf<String, String>()
    parseBody(bodyMap)

    return if(this.method == NanoHTTPD.Method.PUT) {
        //NanoHTTPD will always put the content of a PUT body into a temp file, with the path in the "content" key
        val tmpFileName = bodyMap["content"] ?: return null
        File(tmpFileName).readText()
    }else if(this.method == NanoHTTPD.Method.POST) {
        //NanoHTTPD will put small (less than 1024 bytes) content into the memory, otherwise it will make a file
        val mapContent = bodyMap["postData"] ?: return null
        val tmpFile = File(mapContent)
        if(tmpFile.exists()) {
            tmpFile.readText()
        }else {
            mapContent
        }
    }else {
        throw IllegalArgumentException("receiveRequestBody: this is only available for PUT and POST requests")
    }

}