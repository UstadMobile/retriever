package com.ustadmobile.retriever

interface Retriever{

    suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: ProgressListener,
    )

    fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    companion object{
        const val DBNAME: String = "retreiverdb"

        const val LOGTAG = "RetrieverLog"
    }

}