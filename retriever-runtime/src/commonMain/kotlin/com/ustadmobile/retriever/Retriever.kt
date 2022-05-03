package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.fetcher.RetrieverListener

interface Retriever{

    /**
     * Download the URLs as per the request and save thm
     */
    suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: RetrieverListener,
    )

    suspend fun addFiles(files: List<LocalFileInfo>)

    suspend fun getAllLocallyStoredFiles(): List<LocallyStoredFile>

    suspend fun getLocallyStoredFilesByUrls(urls: List<String>): List<LocallyStoredFile>

    suspend fun deleteFilesByUrl(urls: List<String>)

    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    suspend fun listeningPort(): Int

    /**
     * Stop any HTTP servers, stop availability management monitoring. Does not stop any downloads in progress.
     */
    fun close()

    companion object{
        const val DBNAME: String = "retreiverdb"

        const val LOGTAG = "RetrieverLog"

        const val STATUS_QUEUED = 4

        const val STATUS_RUNNING = 12

        internal const val STATUS_COMPLETE_MIN = 20

        const val STATUS_SUCCESSFUL = 24

        const val STATUS_FAILED = 26

        /**
         * By default nodes will be struck off if they fail a certain
         * number of times within a given period. This is the default
         * period (3 minutes)
         */
        const val DEFAULT_NODE_FAILURE_STRIKEOFF_PERIOD = (3 * 60 * 1000).toLong()

    }

}