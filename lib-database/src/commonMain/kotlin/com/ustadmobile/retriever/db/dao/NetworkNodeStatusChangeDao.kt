package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.lib.db.entities.NetworkNodeStatusChange

@Dao
abstract class NetworkNodeStatusChangeDao {

    @Query("""
        SELECT *
          FROM NetworkNodeStatusChange
    """)
    abstract suspend fun findAll(): List<NetworkNodeStatusChange>

    @Query("""
        DELETE 
          FROM NetworkNodeStatusChange
    """)
    abstract suspend fun clear()

}