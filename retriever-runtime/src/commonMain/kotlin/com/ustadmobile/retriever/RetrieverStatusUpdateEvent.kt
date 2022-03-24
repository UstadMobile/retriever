package com.ustadmobile.retriever

data class RetrieverStatusUpdateEvent(
    val downloadJobItemUid: Long,
    val url: String,
    val status: Int,
) {
}