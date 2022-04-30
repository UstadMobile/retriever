package com.ustadmobile.retriever.util

import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.db.entities.NetworkNodeFailure
import com.ustadmobile.retriever.db.entities.NetworkNodeSuccess
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverNodeHandler
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.stub

@Suppress("UNCHECKED_CAST")
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

        }

        onBlocking {
            handleNetworkNodeSuccessful(any(), any())
        }.thenAnswer { invocation ->
            val db = invocation.arguments.first() as RetrieverDatabase
            val successes = invocation.arguments[1] as List<NetworkNodeSuccess>

            runBlocking {
                successes.groupBy { it.successNodeId }.forEach {
                    db.networkNodeDao.updateLastSuccessTime(it.key, it.value.maxOf { it.successTime })
                }
            }
        }
    }
}