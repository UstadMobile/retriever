package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.NetworkNode

interface  AvailabilityChecker {
    suspend fun checkAvailability(networkNode: NetworkNode, originUrls:List<String>)
}