package com.ustadmobile.retriever.fetcher

data class RetrieverProgressEvent(
    val downloadJobItemUid: Long,
    val url: String,
    val bytesSoFar: Long,
    val localBytesSoFar: Long,
    val originBytesSoFar: Long,
    val totalBytes: Long,
    val status: Int,
)
