package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import com.ustadmobile.retriever.util.findAvailableRandomPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

/**
 * At the moment, this is really just used for JVM Integration test
 */
class RetrieverJvm(
    db: RetrieverDatabase,
    nsdServiceName: String,
    availabilityChecker: AvailabilityChecker,
    originServerFetcher: OriginServerFetcher,
    localPeerFetcher: LocalPeerFetcher,
    json: Json,
    port: Int,
    strikeOffTimeWindow: Long,
    strikeOffMaxFailures: Int,
    availabilityManagerFactory: AvailabilityManagerFactory,
    retrieverCoroutineScope: CoroutineScope,
) : RetrieverCommonJvm(
    db, nsdServiceName, availabilityChecker, originServerFetcher, localPeerFetcher, port, strikeOffTimeWindow,
    strikeOffMaxFailures, json, availabilityManagerFactory, retrieverCoroutineScope
) {

    override suspend fun choosePort(): Int {
        return findAvailableRandomPort()
    }
}