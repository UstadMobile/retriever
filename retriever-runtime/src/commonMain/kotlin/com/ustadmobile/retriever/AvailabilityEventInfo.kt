package com.ustadmobile.retriever

class AvailabilityEventInfo(
    val url: String,
    val available: Boolean,
    val checksPending: Boolean,
    val availableEndpoints: List<String>,
) {

}