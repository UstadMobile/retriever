package com.ustadmobile.retriever.db.callback

import com.ustadmobile.door.DoorDatabaseCallbackStatementList
import com.ustadmobile.door.DoorSqlDatabase

/**
 * Simple database callback that will delete info which is, in fact, temporary.
 */
val DELETE_NODE_INFO_CALLBACK = object: DoorDatabaseCallbackStatementList {
    override fun onCreate(db: DoorSqlDatabase): List<String> {
        return listOf()
    }

    override fun onOpen(db: DoorSqlDatabase): List<String> {
        return listOf(
            "DELETE FROM NetworkNode",
            "DELETE FROM NetworkNodeFailure",
            "DELETE FROM AvailabilityResponse",
            "DELETE FROM AvailabilityObserverItem")
    }
}