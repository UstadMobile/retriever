package com.ustadmobile.lib.db.entities

import kotlinx.serialization.Serializable

@Serializable
class NetworkNodeAndLastFailInfo : NetworkNode(){

    var lastFailTime: Long = 0

    var failCount: Int = 0

}