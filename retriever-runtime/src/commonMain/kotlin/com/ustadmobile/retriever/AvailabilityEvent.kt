package com.ustadmobile.retriever

class AvailabilityEvent(

    /**
     * Map of url to info about the local availability of the file.
     */
    val availabilityInfo: Map<String, AvailabilityEventInfo>,

    val networkNodeUid: Long,

    /**
     * If true, then there are still more local nodes that we don't have an up-to-date response from. If false, we have
     */
    val checksPending: Boolean,
) {

    val originUrlsToAvailable: Map<String, Boolean> by lazy(LazyThreadSafetyMode.NONE) {
        availabilityInfo.map { it.key to it.value.available }.toMap()
    }

}