package com.ustadmobile.core.db.dao

import androidx.room.*
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.lib.db.entities.NetworkNode

@Dao
abstract class NetworkNodeDao: BaseDao<NetworkNode> {

    @Query("""
        SELECT COALESCE(
               (SELECT networkNodeId
                  FROM NetworkNode
                 WHERE networkNodeEndpointUrl = :endpointUrl), 0) 
    """)
    abstract suspend fun findUidByEndpointUrl(endpointUrl: String): Int

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

}