package com.ustadmobile.retriever

import android.content.Context
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.DELETE_NODE_INFO_CALLBACK
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import io.ktor.client.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

    fun build() : RetrieverCommon {
        val db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class, DB_NAME)
            .addCallback(DELETE_NODE_INFO_CALLBACK)
            .build()

        return RetrieverAndroidImpl(db, nsdServiceName, context, AvailabilityCheckerHttp(ktorClient),
            OriginServerFetcher(okHttpClient), LocalPeerFetcher(okHttpClient, json), json)
    }

    companion object {
        private const val DB_NAME = "retrieverdb"

        fun builder(
            context: Context,
            nsdServiceName: String,
            ktorClient: HttpClient,
            okHttpClient: OkHttpClient,
            json: Json,
        ) = RetrieverBuilderAndroid(context, nsdServiceName, ktorClient, okHttpClient, json)

    }

}