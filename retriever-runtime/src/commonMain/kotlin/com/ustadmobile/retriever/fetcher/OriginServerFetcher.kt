package com.ustadmobile.retriever.fetcher

import com.ustadmobile.retriever.db.entities.DownloadJobItem

/**
 * Simple single item fetcher that will download from a given url and save to a given destination using a get request.
 * This is used when downloading from the original origin url (e.g. where an item is not available locally).
 *
 * TODO: define behavior with accept-encoding (e.g. gzip) - probably best set to none
 */
expect class OriginServerFetcher {

    suspend fun download(
        downloadJobItems: List<DownloadJobItem>,
        retrieverListener: RetrieverListener,
    )

}