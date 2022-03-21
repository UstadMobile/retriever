package com.ustadmobile.retriever.fetcher

fun interface RetrieverProgressListener {

    suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent)

}