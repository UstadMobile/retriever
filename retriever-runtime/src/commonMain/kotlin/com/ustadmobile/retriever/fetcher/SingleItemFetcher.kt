package com.ustadmobile.retriever.fetcher

import com.ustadmobile.lib.db.entities.DownloadJobItem

/**
 * Simple single item fetcher that will download from a given url and save to a given destination using a get request.
 * This is used when downloading from the original origin url (e.g. where an item is not available locally).
 *
 * TODO: define behavior with accept-encoding (e.g. gzip) - probably best set to none
 */
expect class SingleItemFetcher {

    suspend fun download(
        downloadJobItem: DownloadJobItem,
        retrieverProgressListener: RetrieverProgressListener,
    )

}