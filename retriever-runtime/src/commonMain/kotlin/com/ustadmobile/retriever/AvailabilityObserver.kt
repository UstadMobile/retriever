package com.ustadmobile.retriever

abstract class AvailabilityObserver(val originUrls: List<String>){

    abstract var urls2: List<String>
    abstract fun onAvailabilityChanged(evt: AvailabilityEvent)
}