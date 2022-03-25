package com.ustadmobile.retriever

data class RetrieverStatusUpdateEvent(
    val downloadJobItemUid: Int,
    val url: String,
    val status: Int,
) {
}