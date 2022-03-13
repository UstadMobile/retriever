package com.ustadmobile.retriever.fetcher

class FetchProgressEvent(
    val downloadJobItemUid: Long,
    val bytesSoFar: Long,
    val totalBytes: Long
)
