package com.ustadmobile.retriever

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.responder.AvailabilityResponder
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

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
        if(!nodeEndpoint.endsWith("/")){
            nodeEndpoint += "/"
        }
        nodeEndpoint += AvailabilityResponder.PARAM_FILE_REQUEST_URL

        var connection: HttpURLConnection? = null

        val requestBodyStr = Gson().toJson(originUrls).toString()
        val bodyByteArray: ByteArray = requestBodyStr.toByteArray(Charsets.UTF_8)

        val url = URL(nodeEndpoint)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true



        val fileAvailableResponses: List<FileAvailableResponse>? =
          try{
              val os: OutputStream = connection.outputStream
              os.write(bodyByteArray, 0, bodyByteArray.size)
              os.flush()
              connection.outputStream.flush()
              connection.outputStream.close()
              os.close()
              connection.connect()
              val responseStr = connection.inputStream.bufferedReader().readText()
              val responseEntryList =
               Gson().fromJson<List<FileAvailableResponse>>(
                  responseStr,
                    object : TypeToken<List<FileAvailableResponse>>() {
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
        TODO("Remove this")
        //return AvailabilityCheckerResult(requestMap, networkNode.networkNodeId)

    }


}