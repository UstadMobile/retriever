package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.core.db.dao.BaseDao
import com.ustadmobile.lib.db.entities.AvailableFile

@Dao
abstract class AvailableFileDao: BaseDao<AvailableFile> {

    @Query("""
        SELECT * FROM AvailableFile 
         WHERE availableFileUrl = :fileUrl
    """)
    abstract fun isFileAvailable(fileUrl: String): List<AvailableFile>

    @Query("""
        SELECT * FROM AvailableFile
    """)
    abstract fun findAllAvailableFiles(): List<AvailableFile>


}