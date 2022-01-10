package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.core.db.dao.BaseDao
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.AvailabilityObserverItemWithNetworkNode

@Dao
abstract class AvailabilityObserverItemDao: BaseDao<AvailabilityObserverItem> {

    //Return a list of AvailabilityObserverItem and NetworkNodeId for all Items without an availability resposne
    @Query("""
        SELECT AvailabilityObserverItem.* , NetworkNode.networkNodeId
          FROM AvailabilityObserverItem 
               JOIN NetworkNode ON NetworkNode.networkNodeId = NetworkNode.networkNodeId 
         WHERE NOT EXISTS (
                SELECT AvailabilityResponse.availabilityUid 
                  FROM AvailabilityResponse 
                 WHERE AvailabilityResponse.availabilityNetworkNode = NetworkNode.networkNodeId
                   AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                )   
    """)
    abstract suspend fun findPendingItems(): List<AvailabilityObserverItemWithNetworkNode>



}