package com.ustadmobile.retriever

abstract class AvailabilityObserver(val originUrls: List<String>){

    abstract fun onAvailabilityChanged(evt: AvailabilityEvent)
}