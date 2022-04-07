package com.ustadmobile.retriever.util

import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.lib.db.entities.NetworkNodeFailure
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverNodeHandler
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.stub

fun RetrieverNodeHandler.mockRecordingFailuresAndNodeStrikeOff(
    strikeOffTimeWindow: Long = (3* 60 * 1000),
    strikeOffMaxFailures: Int = 3,
    block: (List<Int>) -> Unit = {}
) {
    stub {
        onBlocking {
            handleNetworkNodeFailures(any(), any())
        }.thenAnswer { invocation ->
            val db = invocation.arguments.first() as RetrieverDatabase
            val nodeFailures = invocation.arguments[1] as List<NetworkNodeFailure>
            val firstFailTime = nodeFailures.minByOrNull { it.failTime }?.failTime ?: 0L

            runBlocking {
                val timeNow = systemTimeInMillis()
                db.networkNodeFailureDao.insertListAsync(nodeFailures)
                db.networkNodeDao.strikeOffNodes(timeNow - strikeOffTimeWindow, strikeOffMaxFailures,
                    firstFailTime)

                val statusChanges = db.networkNodeStatusChangeDao.findAll()
                db.networkNodeStatusChangeDao.clear()
                val (struckOffNodes, restoredNodes) = statusChanges.partition { it.scNewStatus == NetworkNode.STATUS_STRUCK_OFF }
                block(struckOffNodes.map { it.scNetworkNodeId } )
            }

            Unit
        }
    }
}