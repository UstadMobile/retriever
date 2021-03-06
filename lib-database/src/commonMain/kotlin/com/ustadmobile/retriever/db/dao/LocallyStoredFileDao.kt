package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.annotation.SqliteOnly
import com.ustadmobile.retriever.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.entities.LocallyStoredFileAndDownloadJobItem

@Dao
abstract class LocallyStoredFileDao {

    @Insert
    abstract fun insertList(storedFiles: List<LocallyStoredFile>)

    @Insert
    abstract suspend fun insertListAsync(storedFiles: List<LocallyStoredFile>)

    @Query("""
        SELECT * FROM LocallyStoredFile 
         WHERE lsfOriginUrl = :originUrl
    """)
    abstract fun findStoredFile(originUrl: String): LocallyStoredFile?

    @Query("""
        SELECT LocallyStoredFile.*
          FROM LocallyStoredFile
         WHERE LocallyStoredFile.lsfOriginUrl IN (:originUrls)  
    """)
    abstract fun findLocallyStoredFilesByUrlList(originUrls: List<String>): List<LocallyStoredFile>

    @Query("""
        SELECT * FROM LocallyStoredFile
    """)
    abstract suspend fun findAllLocallyStoredFiles(): List<LocallyStoredFile>

    @Query("""
        DELETE FROM LocallyStoredFile WHERE locallyStoredFileUid = :uid
    """)
    abstract suspend fun removeFile(uid: Int)


    @Query("""
        SELECT LocallyStoredFile.*, DownloadJobItem.*
          FROM DownloadJobItem
               LEFT JOIN LocallyStoredFile
                    ON LocallyStoredFile.lsfOriginUrl = DownloadJobItem.djiOriginUrl
    """)
    @SqliteOnly
    abstract fun findAllAvailableFilesLive(): DoorLiveData<List<LocallyStoredFileAndDownloadJobItem>>

}