package com.ustadmobile.retriever.db.callback

import com.ustadmobile.door.DoorDatabaseCallbackStatementList
import com.ustadmobile.door.DoorSqlDatabase

val NODE_STATUS_CHANGE_TRIGGER_CALLBACK = object: DoorDatabaseCallbackStatementList {

    override fun onCreate(db: DoorSqlDatabase): List<String> {
        return listOf(
            """
            CREATE TRIGGER node_status_trigger AFTER UPDATE ON NetworkNode
              WHEN (NEW.networkNodeStatus != OLD.networkNodeStatus)
             BEGIN INSERT INTO NetworkNodeStatusChange(scNetworkNodeId, scNewStatus)
                          VALUES(NEW.networkNodeId, NEW.networkNodeStatus);
               END
            """)
    }

    override fun onOpen(db: DoorSqlDatabase): List<String> {
        return listOf()
    }
}