package com.ustadmobile.retriever

import android.content.Context
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.RetrieverCommon.Companion.DB_NAME
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.DELETE_NODE_INFO_CALLBACK
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Usage : RetrieverBuilder.builder(context, "serviceName").build()
 */
@Suppress("MemberVisibilityCanBePrivate") // These are part of the API surface
class RetrieverBuilderAndroid private constructor(
    private val context: Context,
    private val nsdServiceName: String,
    private val ktorClient: HttpClient,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
){

    var dbName: String = DB_NAME

    var port: Int = 0

    var retrieverCoroutineScope : CoroutineScope= GlobalScope

    /**
     * Nodes will be struck off if they fail more than a given number of times within a given time window. The default
     * time window is 3 minutes
     */
    var strikeOffTimeWindow: Long = (3 * 60 * 1000)

    /**
     * Nodes will be struck off if they fail more than a given number of times within a given time window. By default a node that
     * has failed 3 times within the strikeOffTimeWindow will be struck off.
     */
    var strikeOffMaxFailures: Int = 3

    /**
     * the default interval for pinging other nodes (every 30 seconds by default)
     */
    var pingInterval : Long= 30 * 1000

    /**
     * The interval to ping nodes after it has failed once, but before it has been "struck off"
     */
    var pingRetryInterval : Long = 5 * 1000

    /**
     * The timeout for a ping reply
     */
    var pingTimeout: Long = 5000

    /**
     * The types of integrity checksums to support
     */
    var integrityChecksums: List<IntegrityChecksum> = listOf(IntegrityChecksum.SHA256)


    fun build() : RetrieverCommon {
        val startTime = systemTimeInMillis()
        val db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class, dbName)
            .addCallback(DELETE_NODE_INFO_CALLBACK)
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build()

        val integrityChecksumsArr = integrityChecksums.toTypedArray()
        val config = RetrieverConfig(nsdServiceName, strikeOffTimeWindow, strikeOffMaxFailures, pingInterval,
            pingRetryInterval, pingTimeout, port, integrityChecksumsArr)

        val retriever = RetrieverAndroidImpl(db, config, context, AvailabilityCheckerHttp(ktorClient, json),
            OriginServerFetcher(okHttpClient, integrityChecksumsArr),
            LocalPeerFetcher(okHttpClient, json, integrityChecksumsArr), json,
            DefaultAvailabilityManagerFactory(), PingerHttp(ktorClient, pingTimeout), retrieverCoroutineScope)
        Napier.d("Retriever: Built retriever in ${systemTimeInMillis() - startTime}ms")
        retriever.start()
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