package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.entities.NetworkNode

interface  AvailabilityChecker {

    suspend fun checkAvailability(
        networkNode: NetworkNode,
        originUrls:List<String>
    ): AvailabilityCheckerResult
}