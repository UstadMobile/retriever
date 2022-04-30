package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.retriever.db.entities.AvailabilityObserverItem
import com.ustadmobile.retriever.db.entities.AvailabilityResponse
import com.ustadmobile.retriever.db.entities.FileAvailabilityWithListener

@Dao
abstract class AvailabilityResponseDao {

    @Insert
    abstract suspend fun insertList(responses: List<AvailabilityResponse>)

    @Query("""
            WITH AffectedObservers AS 
             (SELECT DISTINCT AOI.aoiListenerUid AS affectedListenerUid
               FROM AvailabilityResponse AS AR
               JOIN AvailabilityObserverItem AS AOI
                 ON AR.availabilityOriginUrl = AOI.aoiOriginalUrl
              WHERE AR.availabilityResponseTimeLogged = :responseTimeFilter
              )
            SELECT 
                AvailabilityObserverItem.aoiListenerUid AS listenerUid, 
                AvailabilityObserverItem.aoiOriginalUrl AS fileUrl, 
                EXISTS
                (
                 SELECT 1 
                   FROM AvailabilityResponse
                  WHERE AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                    AND CAST(AvailabilityResponse.availabilityAvailable AS INTEGER) = 1 
                    AND :maxPeerNodeFailuresAllowed > 
                         COALESCE((SELECT COUNT(*)
                                     FROM NetworkNodeFailure
                                    WHERE NetworkNodeFailure.failNetworkNodeId = AvailabilityResponse.availabilityNetworkNode), 0)
                  LIMIT 1
                ) AS available,
                EXISTS
                (
                 SELECT NetworkNode.networkNodeId
                   FROM NetworkNode 
                  WHERE NOT EXISTS(
                        SELECT AvailabilityResponse.availabilityResponseUid
                          FROM AvailabilityResponse
                         WHERE AvailabilityResponse.availabilityNetworkNode = NetworkNode.networkNodeId
                           AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl)
                    AND :maxPeerNodeFailuresAllowed > 
                        COALESCE((SELECT COUNT(*) 
                                   FROM NetworkNodeFailure
                                  WHERE NetworkNodeFailure.failNetworkNodeId = NetworkNode.networkNodeId
                                    AND NetworkNodeFailure.failTime > :countFailuresSince), 0)       
                ) as checksPending,
                NetworkNode.networkNodeEndpointUrl AS networkNodeEndpointUrl
           FROM AvailabilityObserverItem
                LEFT JOIN AvailabilityResponse
                     ON AvailabilityObserverItem.aoiResultMode = ${AvailabilityObserverItem.MODE_INC_AVAILABLE_NODES}
                        AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                        AND CAST(AvailabilityResponse.availabilityAvailable AS INTEGER) = 1
                LEFT JOIN NetworkNode
                     ON AvailabilityObserverItem.aoiResultMode = ${AvailabilityObserverItem.MODE_INC_AVAILABLE_NODES}
                        AND NetworkNode.networkNodeId = AvailabilityResponse.availabilityNetworkNode
          WHERE (:responseTimeFilter = 0 
                 OR AvailabilityObserverItem.aoiListenerUid IN 
                       (SELECT affectedListenerUid FROM AffectedObservers))
            AND (:listenerUidFilter = 0 OR AvailabilityObserverItem.aoiListenerUid = :listenerUidFilter)
       """)
    abstract fun findAllListenersAndAvailabilityByTime(
        responseTimeFilter: Long,
        listenerUidFilter: Int,
        maxPeerNodeFailuresAllowed: Int,
        countFailuresSince: Long,
    ): List<FileAvailabilityWithListener>

    /**
     *
     */
    @Query("""
        SELECT DISTINCT AvailabilityObserverItem.aoiListenerUid
          FROM AvailabilityResponse
               JOIN AvailabilityObserverItem
                    ON AvailabilityObserverItem.aoiOriginalUrl = AvailabilityResponse.availabilityOriginUrl
         WHERE AvailabilityResponse.availabilityNetworkNode = :networkNodeId
           AND CAST(AvailabilityResponse.availabilityAvailable AS INTEGER) = 1
    """)
    abstract suspend fun findListenersAffectedByNodeStruckOff(networkNodeId: Int): List<Int>


    @Query("""
        DELETE 
          FROM AvailabilityResponse
         WHERE availabilityNetworkNode = :networkNodeId
    """)
    abstract suspend fun deleteByNetworkNode(networkNodeId: Long)

    @Query("""
        DELETE FROM AvailabilityResponse
    """)
    abstract suspend fun clearAllResponses()

   companion object{

   }


}