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
    abstract suspend fun findPendingItems(
        maxPeerNodeFailuresAllowed: Int,
        countFailuresSince: Long,
    ): List<AvailabilityObserverItemWithNetworkNode>

    @Query(QUERY_FINDPENDINGITEMS)
    abstract fun findPendingItemsAsync(
        maxPeerNodeFailuresAllowed: Int,
        countFailuresSince: Long,
    ): List<AvailabilityObserverItemWithNetworkNode>

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
                  JOIN NetworkNode ON NetworkNode.networkNodeId = NetworkNode.networkNodeId 
                       AND :maxPeerNodeFailuresAllowed <= 
                        COALESCE((SELECT COUNT(*) 
                                   FROM NetworkNodeFailure
                                  WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                                     AND NetworkNodeFailure.failTime > :countFailuresSince), 0)
            WHERE NOT EXISTS (
                  SELECT AvailabilityResponse.availabilityOriginUrl 
                    FROM AvailabilityResponse 
                   WHERE AvailabilityResponse.availabilityNetworkNode = NetworkNode.networkNodeId
                     AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                  )
       """)
    abstract suspend fun findObserverIdsAffectedByNodeFailure(
        maxPeerNodeFailuresAllowed: Int,
        countFailuresSince: Long,
    ): List<Int>

   companion object{
       const val QUERY_FINDPENDINGITEMS= """
         SELECT AvailabilityObserverItem.* , NetworkNode.*
          FROM AvailabilityObserverItem 
               JOIN NetworkNode ON NetworkNode.networkNodeId = NetworkNode.networkNodeId 
                    AND :maxPeerNodeFailuresAllowed > 
                        COALESCE((SELECT COUNT(*) 
                                   FROM NetworkNodeFailure
                                  WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                                    AND NetworkNodeFailure.failTime > :countFailuresSince), 0)
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