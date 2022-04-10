package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.CoroutineScope

fun interface AvailabilityManagerFactory {

    fun makeAvailabilityManager(
        database: RetrieverDatabase,
        availabilityChecker: AvailabilityChecker,
        strikeOffMaxFailures: Int,
        strikeOffTimeWindow: Long,
        retryDelay: Long,
        nodeHandler: RetrieverNodeHandler,
        retrieverCoroutineScope: CoroutineScope,
    ): AvailabilityManager
}