package com.ustadmobile.retriever

class AvailabilityCheckerResult(
    val results: List<FileAvailableResponse>,
    val networkNodeId: Int,
) {

    fun hasOriginUrl(originUrl: String) = results.any { it.originUrl == originUrl }

}