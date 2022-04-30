package com.ustadmobile.retriever

import com.ustadmobile.retriever.db.entities.AvailabilityObserverItem

/**
 * Availability Observer
 */
class AvailabilityObserver(
    val originUrls: List<String>,
    val onAvailabilityChanged: OnAvailabilityChanged,
    val observerMode: Int = AvailabilityObserverItem.MODE_SUMMARY_ONLY
)
