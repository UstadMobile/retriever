package com.ustadmobile.retriever

class AvailabilityEvent(
    // Do we need to specify which node it is available on or is that unnecessary?
    val originUrlsToAvailable: Map<String, Boolean>,
    val networkNodeUid: Long,

    //Add listener?
    val observer: AvailabilityObserver


    )