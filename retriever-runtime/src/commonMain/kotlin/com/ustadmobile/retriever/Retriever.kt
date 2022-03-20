package com.ustadmobile.retriever

interface Retriever{

    /**
     * Download the URLs as per the request and save thm
     */
    suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: ProgressListener,
    )

    suspend fun addFiles(files: List<LocalFileInfo>)

    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    companion object{
        const val DBNAME: String = "retreiverdb"

        const val LOGTAG = "RetrieverLog"
    }

}