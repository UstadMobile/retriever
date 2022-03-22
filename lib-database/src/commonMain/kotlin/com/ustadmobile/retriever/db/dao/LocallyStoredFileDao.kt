package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.core.db.dao.BaseDao
import com.ustadmobile.door.DoorDataSourceFactory
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.annotation.SqliteOnly
import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.lib.db.entities.LocallyStoredFileAndDownloadJobItem

@Dao
abstract class LocallyStoredFileDao: BaseDao<LocallyStoredFile> {

    @Query("""
        SELECT * FROM LocallyStoredFile 
         WHERE lsfOriginUrl = :fileUrl
    """)
    abstract fun isFileAvailable(fileUrl: String): List<LocallyStoredFile>


    @Query("""
        SELECT * FROM LocallyStoredFile 
         WHERE lsfOriginUrl = :fileUrl
    """)
    abstract suspend fun isFileAvailableAsync(fileUrl: String): List<LocallyStoredFile>


    @Query("""
        SELECT * FROM LocallyStoredFile 
         WHERE lsfOriginUrl = :originUrl
    """)
    abstract fun findAvailableFile(originUrl: String): LocallyStoredFile?

    @Query("""
        SELECT LocallyStoredFile.*
          FROM LocallyStoredFile
         WHERE LocallyStoredFile.lsfOriginUrl IN (:originUrls)  
    """)
    abstract fun findAvailableFilesByUrlList(originUrls: List<String>): List<LocallyStoredFile>

    @Query("""
        SELECT * FROM LocallyStoredFile
    """)
    abstract fun findAllAvailableFiles(): List<LocallyStoredFile>

    @Query("""
        DELETE FROM LocallyStoredFile
    """)
    abstract fun removeAllAvailableFiles()

    @Query("""
        DELETE FROM LocallyStoredFile WHERE locallyStoredFileUid = :uid
    """)
    abstract suspend fun removeFile(uid: Long )


    @Query("""
        SELECT LocallyStoredFile.*, DownloadJobItem.*
          FROM DownloadJobItem
               LEFT JOIN LocallyStoredFile
                    ON LocallyStoredFile.lsfOriginUrl = DownloadJobItem.djiOriginUrl
    """)
    @SqliteOnly
    abstract fun findAllAvailableFilesLive(): DoorLiveData<List<LocallyStoredFileAndDownloadJobItem>>

}