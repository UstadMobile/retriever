package com.ustadmobile.core.db.dao

import androidx.room.*
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.lib.db.entities.NetworkNodeAndLastFailInfo

@Dao
abstract class NetworkNodeDao: BaseDao<NetworkNode> {

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
        SELECT * FROM NetworkNode WHERE networkNodeLost = 0
    """)
    abstract fun findAllActiveNodes(): List<NetworkNode>


    @Query("""
        SELECT * FROM NetworkNode WHERE networkNodeLost = 0
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

}