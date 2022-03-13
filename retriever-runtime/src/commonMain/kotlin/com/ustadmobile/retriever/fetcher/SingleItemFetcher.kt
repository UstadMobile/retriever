package com.ustadmobile.retriever.fetcher

import com.ustadmobile.lib.db.entities.DownloadJobItem

/**
 * Simple single item fetcher that will download from a given url and save to a given destination. This is used when
 * downloading from the original origin url (e.g. where an item is not available locally).
 */
expect class SingleItemFetcher {

    suspend fun download(
        downloadJobItem: DownloadJobItem,
        fetchProgressListener: FetchProgressListener,
    )

}