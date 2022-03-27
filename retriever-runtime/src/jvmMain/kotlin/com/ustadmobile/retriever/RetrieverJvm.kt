package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
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
) : RetrieverCommonJvm(db, nsdServiceName, availabilityChecker, originServerFetcher, localPeerFetcher, json) {




}