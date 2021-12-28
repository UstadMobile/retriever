package com.ustadmobile.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.lib.db.entities.NetworkNode

@Dao
@Repository
abstract class NetworkNodeDao: BaseDao<NetworkNode>{


    @Query("""
        SELECT * FROM NetworkNode where networkNodeEndpointUrl = :endpointUrl
    """)
    abstract suspend fun findByEndpointUrl(endpointUrl: String): List<NetworkNode>

    @Update
    abstract suspend fun updateAsync(networkNode: NetworkNode)

}