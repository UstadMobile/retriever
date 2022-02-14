package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.core.db.dao.BaseDao
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.annotation.SqliteOnly
import com.ustadmobile.lib.db.entities.AvailableFile

@Dao
abstract class AvailableFileDao: BaseDao<AvailableFile> {

    @Query("""
        SELECT * FROM AvailableFile 
         WHERE afOriginUrl = :fileUrl
    """)
    abstract fun isFileAvailable(fileUrl: String): List<AvailableFile>


    @Query("""
        SELECT * FROM AvailableFile 
         WHERE afOriginUrl = :originUrl
    """)
    abstract fun findAvailableFile(originUrl: String): AvailableFile?

    @Query("""
        SELECT * FROM AvailableFile
    """)
    abstract fun findAllAvailableFiles(): List<AvailableFile>

    @Query("""
        DELETE FROM AvailableFile
    """)
    abstract fun removeAllAvailableFiles()

    @Query("""
        DELETE FROM AvailableFile WHERE availableFileUid = :uid
    """)
    abstract suspend fun removeFile(uid: Long )


    @Query("""
        SELECT * FROM AvailableFile
    """)
    @SqliteOnly
    abstract fun findAllAvailableFilesLive(): DoorDataSourceFactory<Int, AvailableFile>

}