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

        val requestMap: Map<String, Boolean> = originUrls.associate {
            val fileUrl: String = it


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
                    RequestResponder.PARAM_FILE_REQUEST_URL + "?" +
                    RequestResponder.PARAM_FILE_REQUEST_URL + "=" +
                    fileUrl


            var connection: HttpURLConnection? = null
            val fileAvailable: Boolean = try {
                val url = URL(nodeEndpoint)
                connection = url.openConnection() as HttpURLConnection
                val responseStr = connection.inputStream.bufferedReader().readText()
                val responseEntryList = Gson().fromJson<List<LocallyStoredFile>>(
                    responseStr,
                    object : TypeToken<List<LocallyStoredFile>>() {
                    }.type
                )
                responseEntryList.isNotEmpty()
            } catch (e: IOException) {
                e.printStackTrace()
                false
            } catch (e: Throwable) {
                e.printStackTrace()
                false
            } finally {
                connection?.disconnect()
            }

            fileUrl to fileAvailable

        }

        return AvailabilityCheckerResult(requestMap, networkNode.networkNodeId)

    }


}