package com.ustadmobile.lib.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Generated whenever another NetworkNode fails.
 */
@Entity
class NetworkNodeFailure {

    @PrimaryKey(autoGenerate = true)
    var networkNodeFailureId: Int = 0

    var failNetworkNodeId: Int = 0

    var failTime: Long = 0

}