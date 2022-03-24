package com.ustadmobile.retriever.fetcher

interface RetrieverProgressListener {

    suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent)

}