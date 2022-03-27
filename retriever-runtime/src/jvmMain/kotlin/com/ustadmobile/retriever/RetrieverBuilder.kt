package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.RetrieverCommon.Companion.DB_NAME
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.DELETE_NODE_INFO_CALLBACK
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import io.ktor.client.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class RetrieverBuilder(
    private val nsdServiceName: String,
    private val ktorClient: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    var dbName: String = DB_NAME

    fun build(): Retriever{
        val db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, dbName)
            .addCallback(DELETE_NODE_INFO_CALLBACK)
            .build()

        return RetrieverJvm(db, nsdServiceName, AvailabilityCheckerHttp(ktorClient),
            OriginServerFetcher(okHttpClient), LocalPeerFetcher(okHttpClient, json),json)
    }

    companion object {

        fun builder(
            nsdServiceName: String,
            ktorClient: HttpClient,
            okHttpClient: OkHttpClient,
            json: Json,
            block: RetrieverBuilder.() -> Unit = {},
        ) = RetrieverBuilder(nsdServiceName, ktorClient, okHttpClient, json).also(block)
    }

}