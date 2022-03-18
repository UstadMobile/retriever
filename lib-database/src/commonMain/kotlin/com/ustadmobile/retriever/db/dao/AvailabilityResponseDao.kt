package com.ustadmobile.retriever.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.ustadmobile.core.db.dao.BaseDao
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.FileAvailabilityWithListener

@Dao
abstract class AvailabilityResponseDao: BaseDao<AvailabilityResponse> {

    @Query(QUERY_FIND_LISTENER_TO_URL)
    abstract fun findAllListenersAndAvailabilityByTime(time: Long):
            List<FileAvailabilityWithListener>

    @Query("""
        DELETE FROM AvailabilityResponse
    """)
    abstract suspend fun clearAllResponses()

   companion object{

       const val QUERY_FIND_LISTENER_TO_URL= """
            WITH AffectedObservers AS 
             (SELECT DISTINCT AOI.aoiListenerUid AS affectedListenerUid
               FROM AvailabilityResponse AS AR
               JOIN AvailabilityObserverItem AS AOI
                 ON AR.availabilityOriginUrl = AOI.aoiOriginalUrl
              WHERE AR.availabilityResponseTimeLogged = :time
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
                           AND AvailabilityResponse.availabilityOriginUrl = AvailabilityObserverItem.aoiOriginalUrl
                  )         
                ) as checksPending
            FROM AffectedObservers
            JOIN AvailabilityObserverItem
              ON AvailabilityObserverItem.aoiListenerUid = AffectedObservers.affectedListenerUid
           
       """

   }


}