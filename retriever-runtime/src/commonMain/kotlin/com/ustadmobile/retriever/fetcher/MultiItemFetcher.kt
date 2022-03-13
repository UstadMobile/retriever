package com.ustadmobile.retriever.fetcher

import com.ustadmobile.lib.db.entities.DownloadJobItem

/**
 * Fetches multiple items from a retriever peer using a post with zip response.
 */
expect class MultiItemFetcher {

    suspend fun download(
        endpointUrl: String,
        downloadJobItems: List<DownloadJobItem>,
        fetchProgressListener: FetchProgressListener,
    ): FetchResult

}