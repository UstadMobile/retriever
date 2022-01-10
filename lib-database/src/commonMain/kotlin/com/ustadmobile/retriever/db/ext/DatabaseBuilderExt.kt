//package com.ustadmobile.core.db.ext
//
//import com.ustadmobile.door.DatabaseBuilder
//import com.ustadmobile.core.db.RetrieverDatabase
//import com.ustadmobile.door.DoorSyncableDatabaseCallback2
//import com.ustadmobile.door.entities.NodeIdAndAuth
//import com.ustadmobile.door.ext.syncableTableIdMap
//
//fun DatabaseBuilder<RetrieverDatabase>.addSyncCallback(
//    nodeIdAndAuth: NodeIdAndAuth,
//    primary: Boolean
//): DatabaseBuilder<RetrieverDatabase> {
//    addCallback(
//        DoorSyncableDatabaseCallback2(nodeIdAndAuth.nodeId,
//        com.ustadmobile.core.db.RetrieverDatabase::class.syncableTableIdMap, primary)
//    )
//
//    return this
//}