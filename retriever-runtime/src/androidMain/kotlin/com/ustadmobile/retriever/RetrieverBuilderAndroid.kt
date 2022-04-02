package com.ustadmobile.retriever

import android.content.Context
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.RetrieverCommon.Companion.DB_NAME
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.DELETE_NODE_INFO_CALLBACK
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Usage : RetrieverBuilder.builder(context, "serviceName").build()
 */
class RetrieverBuilderAndroid private constructor(
    private val context: Context,
    private val nsdServiceName: String,
    private val ktorClient: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
){

    var dbName: String = DB_NAME

    fun build() : RetrieverCommon {
        val startTime = systemTimeInMillis()
        val db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class, dbName)
            .addCallback(DELETE_NODE_INFO_CALLBACK)
            .build()

        val retriever = RetrieverAndroidImpl(db, nsdServiceName, context, AvailabilityCheckerHttp(ktorClient),
            OriginServerFetcher(okHttpClient), LocalPeerFetcher(okHttpClient, json), json)
        Napier.d("Retriever: Built retriever in ${systemTimeInMillis() - startTime}ms")
        return retriever
    }

    companion object {


        fun builder(
            context: Context,
            nsdServiceName: String,
            ktorClient: HttpClient,
            okHttpClient: OkHttpClient,
            json: Json,
        ) = RetrieverBuilderAndroid(context, nsdServiceName, ktorClient, okHttpClient, json)

    }

}