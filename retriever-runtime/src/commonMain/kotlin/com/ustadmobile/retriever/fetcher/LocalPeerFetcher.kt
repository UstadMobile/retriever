package com.ustadmobile.retriever.fetcher

import com.ustadmobile.retriever.db.entities.DownloadJobItem

/**
 * Fetches multiple items from a retriever peer using a post with zip response.
 */
expect class LocalPeerFetcher {

    suspend fun download(
        endpointUrl: String,
        downloadJobItems: List<DownloadJobItem>,
        retrieverListener: RetrieverListener,
    ): FetchResult

}