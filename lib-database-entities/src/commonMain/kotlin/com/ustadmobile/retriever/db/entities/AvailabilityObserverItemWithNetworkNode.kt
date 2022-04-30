package com.ustadmobile.retriever.db.entities

import androidx.room.Embedded

open class AvailabilityObserverItemWithNetworkNode : AvailabilityObserverItem() {

    @Embedded
    var networkNode: NetworkNode = NetworkNode()


}