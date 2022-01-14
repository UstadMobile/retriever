package com.ustadmobile.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.lib.db.entities.NetworkNode

@Dao
abstract class NetworkNodeDao: BaseDao<NetworkNode> {


    @Query("""
        SELECT * FROM NetworkNode where networkNodeEndpointUrl = :endpointUrl
    """)
    abstract suspend fun findAllByEndpointUrl(endpointUrl: String): List<NetworkNode>

    @Query("""
        SELECT * FROM NetworkNode where networkNodeEndpointUrl = :endpointUrl ORDER BY networkNodeDiscovered DESC LIMIT 1
    """)
    abstract suspend fun findByEndpointUrl(endpointUrl: String): NetworkNode?


    @Query("""
        SELECT * FROM NetworkNode WHERE networkNodeLost = 0
    """)
    abstract fun findAllActiveNodes(): List<NetworkNode>

    @Update
    abstract suspend fun updateAsync(networkNode: NetworkNode)

}