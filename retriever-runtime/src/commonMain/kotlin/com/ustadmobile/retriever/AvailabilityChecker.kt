package com.ustadmobile.retriever

interface  AvailabilityChecker {

    suspend fun checkAvailability(
        networkNodeId: Long,
        originUrls:List<String>
    ): AvailabilityCheckerResult

    /** In implementation, get retriever requests from originUrls as:
     * val retrieverRequests: List<RetrieverRequest> = item.fileUrls.map {
     *   RetrieverRequest(it, OriginServerChecksumProvider())
     *   }
     **/
}