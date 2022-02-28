package com.ustadmobile.retriever.util

import java.io.InputStream

class HttpParseUtil {

    fun toByes(responseStream: InputStream): ByteArray{

        val responseString = responseStream.bufferedReader().readText()



        return ByteArray(42)
    }

    fun fromBytes(requestByteArray: ByteArray) {


    }

}