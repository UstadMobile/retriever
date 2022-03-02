package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.core.db.dao.BaseDao
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.AvailabilityObserverItemWithNetworkNode

@Dao
abstract class AvailabilityObserverItemDao: BaseDao<AvailabilityObserverItem> {

    //Return a list of AvailabilityObserverItem and NetworkNodeId for all Items without an availability resposne
    @Query(QUERY_FINDPENDINGITEMS)
    abstract suspend fun findPendingItems(): List<AvailabilityObserverItemWithNetworkNode>

    @Query(QUERY_FINDPENDINGITEMS)
    abstract fun findPendingItemsAsync(): List<AvailabilityObserverItemWithNetworkNode>

    @Query(Companion.QUERY_GET_WATCHLIST_WITH_NUM_NODES)
    abstract fun getWatchListLive(): DoorDataSourceFactory<Int, AvailabilityFileWithNumNodes>

    @Query("""
        DELETE FROM AvailabilityObserverItem WHERE aoiId = :uid
    """)
    abstract suspend fun removeFromWatchList(uid: Long)

   companion object{
       const val QUERY_FINDPENDINGITEMS= """
         SELECT AvailabilityObserverItem.* , NetworkNode.*
          FROM AvailabilityObserverItem 
               JOIN NetworkNode ON NetworkNode.networkNodeId = NetworkNode.networkNodeId 
         WHERE NOT EXISTS (
                SELECT AvailabilityResponse.availabilityOriginUrl 
                  FROM AvailabilityResponse 
                 WHERE AvailabilityResponse.availabilityNetworkNode = NetworkNode.networkNodeId
                   AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                )  """


       const val QUERY_GET_WATCHLIST_WITH_NUM_NODES= """
            SELECT
                AvailabilityObserverItem.* ,
                (SELECT
                    COUNT(DISTINCT AvailabilityResponse.availabilityNetworkNode)      
                FROM
                    AvailabilityResponse                                  
                    LEFT JOIN NetworkNode ON NetworkNode.networkNodeId = AvailabilityResponse.availabilityNetworkNode
                WHERE
                    AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                    AND NetworkNode.networkNodeLost = 0
                ) as numNodes                    
            FROM
                AvailabilityObserverItem
        """
   }


}