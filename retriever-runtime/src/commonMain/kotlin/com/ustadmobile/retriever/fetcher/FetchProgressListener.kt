package com.ustadmobile.retriever.fetcher

fun interface FetchProgressListener {

    fun onFetchProgress(fetchProgressEvent: FetchProgressEvent)

}