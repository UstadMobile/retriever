package com.ustadmobile.retriever.fetcher

data class FetchProgressEvent(
    val downloadJobItemUid: Long,
    val bytesSoFar: Long,
    val totalBytes: Long
)
