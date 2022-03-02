package com.ustadmobile.lib.db.entities

import androidx.room.Embedded

open class AvailabilityObserverItemWithNetworkNode : AvailabilityObserverItem() {

    @Embedded
    var networkNode: NetworkNode = NetworkNode()


}