package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.Retriever.Companion.DEFAULT_NODE_FAILURE_STRIKEOFF_PERIOD
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import com.ustadmobile.retriever.util.mockRecordingFailuresAndNodeStrikeOff
import com.ustadmobile.retriever.util.waitUntilOrTimeout
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.IOException

@Suppress("SpellCheckingInspection")
class PingManagerTest {

    private lateinit var database: RetrieverDatabase

    private lateinit var mockPinger: Pinger

    private val localListeningPort = 1234

    private lateinit var mockNodeHandler: RetrieverNodeHandler

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())

        database = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build().also {
                it.clearAllTables()
            }

        mockPinger = mock {

        }

        mockNodeHandler = mock {}
        mockNodeHandler.mockRecordingFailuresAndNodeStrikeOff {

        }
    }

    private fun insertNetworkNodes(
        numNodes: Int,
        nodeGenerator: (Int) -> NetworkNode = { index ->
            NetworkNode().apply {
                networkNodeEndpointUrl = "http://192.168.1.${index}:$index/"
            }
        }
    ): List<NetworkNode> {
        return runBlocking {
             (0 until numNodes).map { index ->
                nodeGenerator(index).apply {
                    if(lastSuccessTime == 0L)
                        lastSuccessTime = systemTimeInMillis()

                    networkNodeId = database.networkNodeDao.insert(this).toInt()
                }
            }
        }
    }

    @Test
    fun givenNodesNotHeardFromSinceInterval_whenSendPingSuccessful_thenShouldUpdateLastResponseTime() {
        val defaultInterval = 100L
        val retryInterval = 50L
        val timeNow = systemTimeInMillis()
        val initialUpdateTime = timeNow - defaultInterval

        val networkNodes = insertNetworkNodes(4)

        val pingManager = PingManager(database, defaultInterval, retryInterval, 3,
            DEFAULT_NODE_FAILURE_STRIKEOFF_PERIOD, mockPinger, { localListeningPort }, mockNodeHandler, GlobalScope, 2)
        pingManager.start()

        networkNodes.forEach {
            verifyBlocking(mockPinger, timeout(2000).atLeastOnce()) {
                ping(eq(it.networkNodeEndpointUrl!!), any())
            }
        }

        runBlocking {
            database.waitUntilOrTimeout(5000, listOf("NetworkNode")) {
                (it.networkNodeDao.findByUidAsync(networkNodes.first().networkNodeId)?.lastSuccessTime ?: 0)> initialUpdateTime
            }
        }

        val nodeInDb = runBlocking { database.networkNodeDao.findByUidAsync(networkNodes.first().networkNodeId) }
        Assert.assertTrue("Node last success time was updated after successful ping",
            (nodeInDb?.lastSuccessTime ?: 0L) > initialUpdateTime)

        pingManager.close()
    }

    @Test
    fun givenNodeNotHeardFromSinceInterval_whenPingFails_thenWillRetryAndRecordFailures() {
        mockPinger.stub {
            onBlocking {
                ping(any(), any())
            }.thenAnswer {
                throw IOException("Fail!")
            }
        }

        val pingInterval = 10000L

        val badNode = insertNetworkNodes(1) {
            NetworkNode().apply {
                networkNodeEndpointUrl = "http://1.2.3.4:31211/"
                lastSuccessTime = systemTimeInMillis() - (pingInterval + 1)
            }
        }.first()


        val retryInterval = 20L
        val maxPeerFailsAllowed = 3
        val pingManager = PingManager(database, pingInterval, retryInterval, maxPeerFailsAllowed,
            60000, mockPinger, { localListeningPort }, mockNodeHandler, GlobalScope)
        pingManager.start()

        //Should make maxPeerFailsAllowed attempts at contacting the peer within the schedule (+ 100ms buffer time)
        verifyBlocking(mockPinger, timeout((retryInterval * 3) + (100)).atLeast(maxPeerFailsAllowed)) {
            ping(eq(badNode.networkNodeEndpointUrl!!), eq(localListeningPort))
        }

        runBlocking {
            database.waitUntilOrTimeout(5000, listOf("NetworkNodeFailure")) {
                it.networkNodeFailureDao.findByNetworkNodeId(badNode.networkNodeId).size >= maxPeerFailsAllowed
            }
        }

        val nodeFailures = runBlocking { database.networkNodeFailureDao.findByNetworkNodeId(badNode.networkNodeId) }
        Assert.assertTrue("Recorded ping failures", nodeFailures.size >= maxPeerFailsAllowed)

        pingManager.close()
    }

    @Test
    fun givenNodeNotHeardFromSinceInterval_whenWaitingForDurationOfTwoIntervals_thenShouldPingTwice() {
        val node = insertNetworkNodes(1).first()

        val pingInterval = 100L
        val retryInterval = 10L
        val pingManager = PingManager(database, pingInterval, retryInterval, 3,
            60000, mockPinger, { localListeningPort }, mockNodeHandler, GlobalScope)
        pingManager.start()

        verifyBlocking(mockPinger, timeout((pingInterval * 2) + 100).times(2)) {
            ping(eq(node.networkNodeEndpointUrl!!), eq(localListeningPort))
        }

        pingManager.close()
    }

}