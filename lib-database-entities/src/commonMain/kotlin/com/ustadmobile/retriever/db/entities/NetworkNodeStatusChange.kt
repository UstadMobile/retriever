package com.ustadmobile.retriever.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class NetworkNodeStatusChange {

    @PrimaryKey(autoGenerate = true)
    var scId: Int = 0

    var scNetworkNodeId: Int = 0

    var scNewStatus: Int = 0

}