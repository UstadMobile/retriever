package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.retriever.db.entities.DownloadJobItem
import com.ustadmobile.retriever.db.entities.DownloadJobItemAndNodeInfo

@Dao
abstract class DownloadJobItemDao {

    /**
     * Find the next downloads to run. This will try and find hosts that have as many of the files of the download batch
     * as possible.
     * Will exclude peer nodes that have failed too many times within a given time threshold.
     *
     * @param batchId download batch id
     * @param maxFailuresAllowed the maximum number of failures by a given node allowed within the time threshold
     * @param countFailuresSince the time (in millis) from which failures are being counted (eg. now minus 3 mins)
     */
    @Query("""
        WITH AvailableFile AS (
             SELECT DownloadJobItem.djiOriginUrl AS originUrl, 
                    NetworkNode.networkNodeEndpointUrl AS networkNodeEndpointUrl, 
                    NetworkNode.networkNodeId AS networkNodeId
               FROM DownloadJobItem
                    JOIN AvailabilityResponse 
                         ON AvailabilityResponse.availabilityOriginUrl = DownloadJobItem.djiOriginUrl
                            AND CAST(AvailabilityResponse.availabilityAvailable AS INTEGER) = 1
                    JOIN NetworkNode
                         ON NetworkNode.networkNodeId = AvailabilityResponse.availabilityNetworkNode
                            AND :maxFailuresAllowed > 
                                COALESCE((SELECT COUNT(*)
                                           FROM NetworkNodeFailure
                                          WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                                            AND NetworkNodeFailure.failTime > :countFailuresSince), 0)
              WHERE DownloadJobItem.djiBatchId = :batchId
             ),
             AvailableCountPerNode AS (
             SELECT AvailableFile.networkNodeId AS networkNodeId, COUNT(*) AS availableOnNodeCount
               FROM AvailableFile
           GROUP BY AvailableFile.networkNodeId
             )
             
        SELECT DownloadJobItem.*,
               AvailableFile.networkNodeEndpointUrl AS networkNodeEndpointUrl,
               AvailableFile.networkNodeId AS networkNodeId
          FROM DownloadJobItem
			   LEFT JOIN AvailableFile
                    ON DownloadJobItem.djiOriginUrl = AvailableFile.originUrl
                   AND (SELECT AvailableCountPerNode.availableOnNodeCount 
                          FROM AvailableCountPerNode
                         WHERE AvailableCountPerNode.networkNodeId = AvailableFile.networkNodeId) =
                       (SELECT MAX(availableOnNodeCount)
                         FROM AvailableCountPerNode
                        WHERE AvailableCountPerNode.networkNodeId IN 
                              (SELECT AvailableFile.networkNodeId
                                 FROM AvailableFile
                                WHERE AvailableFile.originUrl = DownloadJobItem.djiOriginUrl)) 
               
         WHERE djiBatchId = :batchId
           AND djiStatus = $STATUS_QUEUED   
    """)
    abstract suspend fun findNextItemsToDownload(
        batchId: Long,
        maxFailuresAllowed: Int,
        countFailuresSince: Long,
    ): List<DownloadJobItemAndNodeInfo>

    @Insert
    abstract suspend fun insertList(entities: List<DownloadJobItem>)

    @Query("""
        UPDATE DownloadJobItem
           SET djiStatus = :newStatus
         WHERE djiUid = :uid    
    """)
    abstract suspend fun updateStatusByUid(uid: Int, newStatus: Int)

    @Query("""
        UPDATE DownloadJobItem
           SET djiBytesSoFar = :bytesSoFar,
               djiLocalBytesSoFar = :localBytesSoFar,
               djiOriginBytesSoFar = :originBytesSoFar,
               djiTotalSize = :totalSize
         WHERE djiUid = :uid     
    """)
    abstract suspend fun updateProgressByUid(
        uid: Int,
        bytesSoFar: Long,
        localBytesSoFar: Long,
        originBytesSoFar: Long,
        totalSize: Long,
    )

    @Query("""
        UPDATE DownloadJobItem
           SET djiAttemptCount = :attemptCount,
               djiStatus = :status
         WHERE djiUid = :uid          
    """)
    abstract suspend fun updateStatusAndAttemptCountByUid(
        uid: Int,
        status: Int,
        attemptCount: Int,
    )

    @Query("""
        SELECT NOT EXISTS(
               SELECT djiUid
                 FROM DownloadJobItem
                WHERE djiBatchId = :batchId
                  AND djiStatus < $STATUS_COMPLETE) 
    """)
    abstract suspend fun isBatchDone(batchId: Long): Boolean

    @Query("""
        SELECT DownloadJobItem.*
          FROM DownloadJobItem
         WHERE djiOriginUrl = :url 
    """)
    abstract suspend fun findByUrlFirstOrNull(url: String): DownloadJobItem?

    @Query("""
        SELECT DownloadJobItem.djiOriginUrl
          FROM DownloadJobItem
         WHERE djiBatchId = :batchId
    """)
    abstract suspend fun findAllUrlsByBatchId(batchId: Long): List<String?>

    @Query("""
        DELETE 
          FROM DownloadJobItem
         WHERE djiOriginUrl IN (:urls)  
    """)
    abstract suspend fun deleteByUrlList(urls: List<String>)


    companion object {

        //Copies of values found on Retriever interface so they can be used here:


        internal const val STATUS_QUEUED = 4

        internal const val STATUS_RUNNING = 12

        internal const val STATUS_COMPLETE = 24

        internal const val STATUS_FAILED = 26

    }
}