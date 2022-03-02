package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.NetworkNode

interface  AvailabilityChecker {

    suspend fun checkAvailability(
        networkNode: NetworkNode,
        originUrls:List<String>
    ): AvailabilityCheckerResult

    /** In implementation, get retriever requests from originUrls as:
     * val retrieverRequests: List<RetrieverRequest> = item.fileUrls.map {
     *   RetrieverRequest(it, OriginServerChecksumProvider())
     *   }
     **/
}