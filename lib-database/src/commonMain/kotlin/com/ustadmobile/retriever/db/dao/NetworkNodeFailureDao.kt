package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.lib.db.entities.NetworkNodeFailure

@Dao
abstract class NetworkNodeFailureDao {

    @Insert
    abstract suspend fun insert(networkNodeFailure: NetworkNodeFailure)

    @Insert
    abstract suspend fun insertListAsync(networkNodeFailures: List<NetworkNodeFailure>)

    @Query("""
        INSERT INTO NetworkNodeFailure (failNetworkNodeId, failTime)
        SELECT COALESCE((SELECT NetworkNode.networkNodeId 
                           FROM NetworkNode
                          WHERE NetworkNode.networkNodeEndpointUrl = :endpoint), 0) AS failNetworkNodeId,
              :failTime AS failTime            
    """)
    abstract suspend fun insertFailureUsingEndpoint(endpoint: String, failTime: Long)

    @Query("""
        DELETE
          FROM NetworkNodeFailure
         WHERE failNetworkNodeId = :networkNodeId 
    """)
    abstract suspend fun deleteByNetworkNodeId(networkNodeId: Int)

    @Query("""
        SELECT NetworkNodeFailure.*
          FROM NetworkNodeFailure
         WHERE NetworkNodeFailure.failNetworkNodeId = :networkNodeId 
    """)
    abstract suspend fun findByNetworkNodeId(networkNodeId: Int) : List<NetworkNodeFailure>

    //Get the failure count and last fail time.
    @Query("""
        SELECT COUNT(*) 
          FROM NetworkNodeFailure
         WHERE failNetworkNodeId = :networkNodeId 
           AND failTime > :countFailuresSince
    """)
    abstract suspend fun failureCountForNode(networkNodeId: Int, countFailuresSince: Long): Int


}