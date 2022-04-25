package com.ustadmobile.retriever.db.dao

import androidx.room.*
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.lib.db.entities.NetworkNodeAndLastFailInfo
import com.ustadmobile.lib.db.entities.NetworkNodeRestoreInfo

@Dao
abstract class NetworkNodeDao {

    @Insert
    abstract fun insertList(nodes: List<NetworkNode>)

    @Insert
    abstract fun insert(node: NetworkNode): Long

    @Query("""
        SELECT COALESCE(
               (SELECT networkNodeId
                  FROM NetworkNode
                 WHERE networkNodeEndpointUrl = :endpointUrl), 0) 
    """)
    abstract suspend fun findUidByEndpointUrl(endpointUrl: String): Int

    @Query("""
        SELECT NetworkNode.*
          FROM NetworkNode
         WHERE NetworkNode.networkNodeId = :networkNodeId 
    """)
    abstract suspend fun findByUidAsync(networkNodeId: Int): NetworkNode?

    @Insert
    abstract suspend fun insertNodeAsync(networkNode: NetworkNode): Long

    @Query("""
        SELECT * FROM NetworkNode where networkNodeEndpointUrl = :endpointUrl
    """)
    abstract suspend fun findAllByEndpointUrl(endpointUrl: String): List<NetworkNode>

    @Query("""
        SELECT * FROM NetworkNode where networkNodeEndpointUrl = :endpointUrl ORDER BY networkNodeDiscovered DESC LIMIT 1
    """)
    abstract suspend fun findByEndpointUrl(endpointUrl: String): NetworkNode?

    @Query("""
        DELETE 
          FROM NetworkNode
         WHERE networkNodeId = :networkNodeId 
    """)
    abstract suspend fun deleteByNetworkNodeId(networkNodeId: Int)

    @Query("""
        SELECT * 
          FROM NetworkNode
    """)
    abstract fun findAllActiveNodes(): List<NetworkNode>


    @Query("""
        SELECT * 
          FROM NetworkNode
    """)
    abstract fun findAllActiveNodesLive(): DoorDataSourceFactory<Int, NetworkNode>


    @Query("""
        DELETE FROM NetworkNode WHERE networkNodeId = :uid
    """)
    abstract suspend fun deleteByNetworkNodeId(uid: Long)


    @Update
    abstract suspend fun updateAsync(networkNode: NetworkNode)

    @Query("""
        DELETE FROM NetworkNode
    """)
    abstract suspend fun clearAllNodes()

    @Query("""
        SELECT COALESCE(
               (SELECT networkNodeId
                  FROM NetworkNode
                 WHERE networkNodeEndpointUrl = :endpointUrl), 0)
    """)
    abstract suspend fun findNetworkNodeIdByEndpointUrl(endpointUrl: String): Int

    @Query("""
        SELECT NetworkNode.*,
               (SELECT COUNT(*) 
                  FROM NetworkNodeFailure
                 WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                   AND failTime >= :countFailuresSince) AS failCount,
               (SELECT COALESCE(
                       (SELECT MAX(failTime)
                          FROM NetworkNodeFailure
                         WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId), 0)) AS lastFailTime
          FROM NetworkNode                   
    """)
    abstract suspend fun findNodesWithLastFailInfo(
        countFailuresSince: Long,
    ): List<NetworkNodeAndLastFailInfo>

    @Query("""
        UPDATE NetworkNode
           SET lastSuccessTime = :lastSuccessTime
         WHERE networkNodeId = :networkNodeId  
    """)
    abstract suspend fun updateLastSuccessTime(networkNodeId: Int, lastSuccessTime: Long)

    @Query("""
        UPDATE NetworkNode
           SET networkNodeLost = :lostTime
         WHERE networkNodeId = :networkNodeId  
    """)
    abstract suspend fun updateNetworkNodeLostTime(networkNodeId: Int, lostTime: Long)

    @Query("""
        SELECT NetworkNode.networkNodeId
          FROM NetworkNode
         WHERE networkNodeLost > lastSuccessTime
           AND (SELECT COUNT(*)
                  FROM NetworkNodeFailure
                 WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                   AND NetworkNodeFailure.failTime >= :countFailuresSince) >= :maxFailuresAllowed   
    """)
    abstract suspend fun findNetworkNodesToDelete(
        countFailuresSince: Long,
        maxFailuresAllowed: Int,
    ): List<Int>


    @Query("""
        UPDATE NetworkNode
           SET networkNodeStatus = ${NetworkNode.STATUS_STRUCK_OFF}
         WHERE networkNodeId IN 
               (SELECT DISTINCT failNetworkNodeId
                  FROM NetworkNodeFailure
                 WHERE failTime >= :checkForNodesThatFailedSince)
           AND networkNodeStatus != ${NetworkNode.STATUS_STRUCK_OFF}      
           AND COALESCE(
               (SELECT COUNT(*) 
                  FROM NetworkNodeFailure
                 WHERE failNetworkNodeId = NetworkNode.networkNodeId
                   AND failTime >= :countFailuresSince), 0) >= :maxFailuresAllowed 
    """)
    /**
     * Update the status on nodes that need to be struck off for having failed too many times recently.
     */
    abstract suspend fun strikeOffNodes(
        countFailuresSince: Long,
        maxFailuresAllowed: Int,
        checkForNodesThatFailedSince: Long,
    )

    @Query("""
        UPDATE NetworkNode
           SET networkNodeStatus = ${NetworkNode.STATUS_OK}
         WHERE networkNodeStatus != ${NetworkNode.STATUS_OK}      
           AND (SELECT COUNT(*) 
                  FROM NetworkNodeFailure
                 WHERE failNetworkNodeId = NetworkNode.networkNodeId
                   AND failTime >= :countFailuresSince) < :maxFailuresAllowed
    """)
    abstract suspend fun restoreNodes(
        countFailuresSince: Long,
        maxFailuresAllowed: Int,
    )

    @Query("""
      SELECT NetworkNode.networkNodeId, 
             NetworkNode.lastSuccessTime,
             COALESCE(
                (SELECT failTime
                   FROM NetworkNodeFailure
                  WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                    AND NetworkNodeFailure.failTime >= :countFailuresSince
               ORDER BY NetworkNodeFailure.failTime DESC
                  LIMIT 1
                 OFFSET (:maxFailuresAllowed - 1)), :timeNow) AS restorableTime
        FROM NetworkNode         
       WHERE NetworkNode.networkNodeStatus != ${NetworkNode.STATUS_OK}
         
    """)
    abstract suspend fun findNetworkNodeRestorableTimes(
        countFailuresSince: Long,
        maxFailuresAllowed: Int,
        timeNow: Long
    ): List<NetworkNodeRestoreInfo>

}