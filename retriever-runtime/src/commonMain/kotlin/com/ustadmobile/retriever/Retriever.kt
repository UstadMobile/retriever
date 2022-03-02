package com.ustadmobile.retriever

interface Retriever{

    /**
     * This is a public facing retriever request
     */
    fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall

    suspend fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    companion object{
        const val DBNAME: String = "retreiverdb"
    }

}