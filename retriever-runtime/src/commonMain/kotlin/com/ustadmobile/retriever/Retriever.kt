package com.ustadmobile.retriever

interface Retriever{

    /**
     * This is a public facing retriever request
     */
    fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall

    suspend fun retrieve(
        retrieverRequests: List<RetrieverRequest>,
        progressListener: ProgressListener,
    )

    suspend fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    suspend fun forceStartJob()

    companion object{
        const val DBNAME: String = "retreiverdb"
    }

}