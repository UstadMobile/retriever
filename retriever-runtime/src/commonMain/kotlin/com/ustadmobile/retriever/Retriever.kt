package com.ustadmobile.retriever

import com.ustadmobile.retriever.fetcher.RetrieverProgressListener

interface Retriever{

    /**
     * Download the URLs as per the request and save thm
     */
    suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: RetrieverProgressListener,
    )

    suspend fun addFiles(files: List<LocalFileInfo>)

    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    companion object{
        const val DBNAME: String = "retreiverdb"

        const val LOGTAG = "RetrieverLog"

        const val STATUS_QUEUED = 4

        const val STATUS_RUNNING = 12

        internal const val STATUS_COMPLETE_MIN = 20

        const val STATUS_SUCCESSFUL = 24

        const val STATUS_ATTEMPT_FAILED = 25

        const val STATUS_FAILED = 26

    }

}