package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.CoroutineScope

class DefaultAvailabilityManagerFactory : AvailabilityManagerFactory {

    override fun makeAvailabilityManager(
        database: RetrieverDatabase,
        availabilityChecker: AvailabilityChecker,
        strikeOffMaxFailures: Int,
        strikeOffTimeWindow: Long,
        retryDelay: Long,
        nodeHandler: RetrieverNodeHandler,
        retrieverCoroutineScope: CoroutineScope
    ): AvailabilityManager {
        return AvailabilityManager(database, availabilityChecker, strikeOffMaxFailures, strikeOffTimeWindow, retryDelay,
            nodeHandler, retrieverCoroutineScope)
    }
}