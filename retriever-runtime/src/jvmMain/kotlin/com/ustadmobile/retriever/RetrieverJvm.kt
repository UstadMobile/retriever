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
    config: RetrieverConfig,
    availabilityChecker: AvailabilityChecker,
    originServerFetcher: OriginServerFetcher,
    localPeerFetcher: LocalPeerFetcher,
    json: Json,
    availabilityManagerFactory: AvailabilityManagerFactory,
    retrieverCoroutineScope: CoroutineScope,
) : RetrieverCommonJvm(
    db, config, availabilityChecker, originServerFetcher, localPeerFetcher, json, availabilityManagerFactory,
    retrieverCoroutineScope
) {

    override suspend fun choosePort(): Int {
        return findAvailableRandomPort()
    }
}