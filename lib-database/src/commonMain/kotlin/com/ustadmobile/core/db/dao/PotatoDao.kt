package com.ustadmobile.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.ustadmobile.door.annotation.Repository
import com.ustadmobile.lib.db.entities.Potato

@Dao
@Repository
abstract class PotatoDao: BaseDao<Potato>{

    @Query("SELECT * FROM Potato LIMIT 1")
    abstract fun getFirstPotato(): Potato?

    @Update
    abstract suspend fun updateAsync(potato: Potato)

}