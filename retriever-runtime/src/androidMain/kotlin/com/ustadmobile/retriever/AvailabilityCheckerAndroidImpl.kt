package com.ustadmobile.retriever

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.responder.RequestResponder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.io.bufferedReader
import kotlin.io.readText

class AvailabilityCheckerAndroidImpl(val db: RetrieverDatabase): AvailabilityChecker {


    override suspend fun checkAvailability(
        networkNode: NetworkNode,
        originUrls: List<String>
    ): AvailabilityCheckerResult {

        //For networkNode, find availability of the originalUrls

        var nodeEndpoint = networkNode.networkNodeEndpointUrl ?: ""
        nodeEndpoint = if (nodeEndpoint.startsWith("/")) {
            nodeEndpoint.substring(1, nodeEndpoint.length)
        } else {
            nodeEndpoint
        }
        nodeEndpoint = if (nodeEndpoint.startsWith("http")) {
            nodeEndpoint
        } else {
            "http://$nodeEndpoint"
        }
        nodeEndpoint = nodeEndpoint + "/" +
                RequestResponder.PARAM_FILE_REQUEST_URL

        var connection: HttpURLConnection? = null

        val requestBodyStr = Gson().toJson(originUrls).toString()
        val bodyByteArray: ByteArray = requestBodyStr.toByteArray(Charsets.UTF_8)

        val url = URL(nodeEndpoint)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true



        val fileAvailableResponses: List<RequestResponder.FileAvailableResponse>? =
          try{

            connection.outputStream.write(bodyByteArray, 0, bodyByteArray.size)

            val responseStr = connection.inputStream.bufferedReader().readText()
            val responseEntryList =
             Gson().fromJson<List<RequestResponder.FileAvailableResponse>>(
                responseStr,
                object : TypeToken<List<RequestResponder.FileAvailableResponse>>() {
                }.type
            )

            responseEntryList


         } catch (e: IOException) {
            e.printStackTrace()
            null
         } catch (e: Throwable) {
            e.printStackTrace()
            null
         } finally {
            connection.disconnect()
         }


        //Create the requestMap
        val requestMap: Map<String, Boolean> = originUrls.associate {
            val originUrl = it
            val available: Boolean = fileAvailableResponses?.any { it.originUrl == originUrl }?:false

            originUrl to available
        }

        return AvailabilityCheckerResult(requestMap, networkNode.networkNodeId)

    }


}