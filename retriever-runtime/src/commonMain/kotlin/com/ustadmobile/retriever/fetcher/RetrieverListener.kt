package com.ustadmobile.retriever.fetcher

import com.ustadmobile.retriever.RetrieverStatusUpdateEvent

interface RetrieverListener {

    suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent)

    suspend fun onRetrieverStatusUpdate(retrieverStatusEvent: RetrieverStatusUpdateEvent)

}