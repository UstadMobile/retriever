package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.retriever.RetrieverCommon.Companion.DB_NAME
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.DELETE_NODE_INFO_CALLBACK
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Suppress("MemberVisibilityCanBePrivate") //These are part of the API surface
class RetrieverBuilder(
    private val nsdServiceName: String,
    private val ktorClient: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    var dbName: String = DB_NAME

    var port: Int = 0

    var retrieverCoroutineScope: CoroutineScope = GlobalScope

    /**
     * Nodes will be struck off if they fail more than a given number of times within a given time window. The default
     * time window is 3 minutes
     */
    var strikeOffTimeWindow: Long = (3 * 60 * 1000)

    /**
     * Nodes will be struck off if they fail a given number of times within a given time window. By default a node that
     * has failed 3 times within the strikeOffTimeWindow will be struck off.
     */
    var strikeOffMaxFailures: Int = 3

    fun build(): Retriever{
        val db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, dbName)
            .addCallback(DELETE_NODE_INFO_CALLBACK)
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build()

        val retriever = RetrieverJvm(db, nsdServiceName, AvailabilityCheckerHttp(ktorClient),
            OriginServerFetcher(okHttpClient), LocalPeerFetcher(okHttpClient, json), json, port, strikeOffTimeWindow,
            strikeOffMaxFailures, DefaultAvailabilityManagerFactory(), retrieverCoroutineScope)
        retriever.start()
        return retriever
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