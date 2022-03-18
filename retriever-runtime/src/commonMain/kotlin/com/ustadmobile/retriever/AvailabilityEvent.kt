package com.ustadmobile.retriever

class AvailabilityEvent(
    val originUrlsToAvailable: Map<String, Boolean>,


    val networkNodeUid: Long,

    /**
     * If true, then there are still more local nodes that we don't have an up-to-date response from. If false, we have
     */
    val checksPending: Boolean,
)