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



        println("Retriever: 1")
        val fileAvailableResponses: List<AvailabilityResponder.FileAvailableResponse>? =
          try{
              println("Retriever: 2")
              val os: OutputStream = connection.outputStream
              println("Retriever: 2a")
              os.write(bodyByteArray, 0, bodyByteArray.size)
              println("Retriever: 2b")

              println("Retriever: 2c")
              os.flush()

              //connection.outputStream.write(bodyByteArray, 0, bodyByteArray.size)
              connection.outputStream.flush()
              println("Retriever: 2d")
              connection.outputStream.close()
              os.close()


              println("Retriever: 3")
              connection.connect()
              println("Retriever: 4")

//              val bos = ByteArrayOutputStream()
//              val bis = BufferedInputStream(connection.inputStream)
//              var nextByte = bis.read()
//              while(nextByte != -1){
//                  bos.write(nextByte)
//              }
//              val responseString = bos.toString(nextByte)

              println("Retriever: 5")
              val responseStr = connection.inputStream.bufferedReader().readText()
              println("Retriever: 6")
              val responseEntryList =
               Gson().fromJson<List<AvailabilityResponder.FileAvailableResponse>>(
                  responseStr,
                    object : TypeToken<List<AvailabilityResponder.FileAvailableResponse>>() {
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