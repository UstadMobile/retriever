package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.Retriever.Companion.DEFAULT_NODE_FAILURE_STRIKEOFF_PERIOD
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.util.waitUntilOrTimeout
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verifyBlocking

class PingManagerTest {

    lateinit var database: RetrieverDatabase

    lateinit var mockPinger: PingManager.Pinger

    @Before
    fun setup() {
        database = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }

        mockPinger = mock {

        }
    }

    @Test
    fun givenNodeNotHeardFromSinceInterval_whenSendPingSuccessful_thenShouldUpdateLastResponseTime() {
        val defaultInterval = 100L
        val retryInterval = 50L
        val timeNow = systemTimeInMillis()
        val initialUpdateTime = timeNow - defaultInterval
        val networkNode = NetworkNode().apply {
            networkNodeEndpointUrl = "http://1.2.3.4:1234/"
            lastSuccessTime = initialUpdateTime
            networkNodeId = database.networkNodeDao.insert(this).toInt()
        }


        val pingManager = PingManager(database, defaultInterval, retryInterval, 3,
            DEFAULT_NODE_FAILURE_STRIKEOFF_PERIOD, mockPinger, GlobalScope, 2)

        verifyBlocking(mockPinger, timeout(2000)) {
            ping(networkNode.networkNodeEndpointUrl!!)
        }

        runBlocking {
            database.waitUntilOrTimeout(5000, listOf("NetworkNode")) {
                (it.networkNodeDao.findByUidAsync(networkNode.networkNodeId)?.lastSuccessTime ?: 0)> initialUpdateTime
            }
        }

        val nodeInDb = runBlocking { database.networkNodeDao.findByUidAsync(networkNode.networkNodeId) }
        Assert.assertTrue("Node last success time was updated after successful ping",
            (nodeInDb?.lastSuccessTime ?: 0L) > initialUpdateTime)

        pingManager.close()
    }


    fun givenNodeNotHeardFromSinceInterval_whenWaitingForDurationOfTwoIntervals_thenShouldPingTwice() {

    }

    fun givenIncomingPingRequest_whenSuccessful_thenShouldCheckRemoteNodeWasDiscovered() {

    }

    fun givenNodeNotHeardFromSinceInterval_whenPingFails_thenShouldRecordFailureAndRetry() {

    }


}