package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.AvailabilityFileWithNumNodes
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.AvailabilityObserverItemWithNetworkNode
import com.ustadmobile.lib.db.entities.NetworkNode

@Dao
abstract class AvailabilityObserverItemDao {

    @Insert
    abstract suspend fun insertList(observers: List<AvailabilityObserverItem>)

    //Return a list of AvailabilityObserverItem and NetworkNodeId for all Items without an availability resposne
    @Query(QUERY_FINDPENDINGITEMS)
    abstract suspend fun findPendingItemsAsync(): List<AvailabilityObserverItemWithNetworkNode>

    @Query(QUERY_GET_WATCHLIST_WITH_NUM_NODES)
    abstract fun getWatchListLive(): DoorDataSourceFactory<Int, AvailabilityFileWithNumNodes>

    @Query("""
        DELETE 
          FROM AvailabilityObserverItem 
         WHERE aoiListenerUid = :listenerUid
    """)
    abstract suspend fun deleteByListenerUid(listenerUid: Int)


    /**
     * Find the ids of observers where they were expecting answers on availability checks, however because of failures
     * by that node, these checks are not going to be tried again. This is important so we can deliver a
     * checksPending = false which will be used by the downloader
     */
    @Query("""
           SELECT DISTINCT AvailabilityObserverItem.aoiListenerUid
             FROM AvailabilityObserverItem
                  JOIN NetworkNode 
                       ON NetworkNode.networkNodeStatus = ${NetworkNode.STATUS_STRUCK_OFF}
            WHERE NetworkNode.networkNodeId IN (:failedNodeIds)  
              -- Where we are waiting for pending information
              AND (NOT EXISTS (SELECT AvailabilityResponse.availabilityOriginUrl 
                                 FROM AvailabilityResponse 
                                WHERE AvailabilityResponse.availabilityNetworkNode = NetworkNode.networkNodeId
                                  AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl)
                      -- Where this node was providing the item itself    
                    OR EXISTS (SELECT AvailabilityResponse.availabilityOriginUrl
                                 FROM AvailabilityResponse
                                WHERE AvailabilityResponse.availabilityNetworkNode = NetworkNode.networkNodeId
                                  AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                                  AND CAST(AvailabilityResponse.availabilityAvailable AS INTEGER) = 1))
              
       """)
    abstract suspend fun findObserverIdsAffectedByNodeFailure(failedNodeIds: List<Int>): List<Int>

   companion object{
       const val QUERY_FINDPENDINGITEMS= """
         SELECT AvailabilityObserverItem.* , NetworkNode.*
          FROM AvailabilityObserverItem 
               JOIN NetworkNode ON NetworkNode.networkNodeId = NetworkNode.networkNodeId 
                    AND NetworkNode.networkNodeStatus = ${NetworkNode.STATUS_OK}
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
                    AND CAST(AvailabilityResponse.availabilityAvailable AS INTEGER) = 1
                ) as numNodes                    
            FROM
                AvailabilityObserverItem
        """
   }


}