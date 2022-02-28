package com.ustadmobile.retriever

interface Retriever{

    fun retrieve(retrieverRequests: List<RetrieverRequest>): RetrieverCall

    suspend fun addAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    suspend fun removeAvailabilityObserver(availabilityObserver: AvailabilityObserver)

    companion object{
        const val DBNAME: String = "retreiverdb"
    }

}