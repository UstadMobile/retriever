package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.dao.AvailabilityObserverItemDao
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.*
import kotlin.random.Random

/**
 * JVM Test
 */
class TestAvailabilityManager {


    private lateinit var db: RetrieverDatabase

    private lateinit var availabilityChecker: AvailabilityChecker

    private lateinit var availabilityManager: AvailabilityManager

    private lateinit var context: Any

    private lateinit var availabilityObserver: AvailabilityObserver

    private lateinit var availabilityObserverItemDaoSpy: AvailabilityObserverItemDao

    private lateinit var onAvailabilityChanged: OnAvailabilityChanged

    private val testOriginUrls: List<String> = listOf(
        "http://path.to/file1",
        "http://path.to/file2",
        "http://path.to/file3",
        "http://path.to/file4",
        "http://path.to/file5"
    )

    private val defaultNetworkNodeList: List<NetworkNode> = listOf(
        NetworkNode("100.1.1.1", "100.1.1.1:8081/", DateTime.nowUnixLong(), 1),
        NetworkNode("100.1.1.2", "100.1.1.2:8081/", DateTime.nowUnixLong(), 2),
        NetworkNode("100.1.1.3", "100.1.1.3:8081/", DateTime.nowUnixLong(), 3),
        NetworkNode("100.1.1.4", "100.1.1.4:8081/", DateTime.nowUnixLong(), 4),
        NetworkNode("100.1.1.5", "100.1.1.5:8081/", DateTime.nowUnixLong(), 5)
    )

    private val nodeAvailabilityMaps = (0..4).map {
        (1..5).map { "http://path.to/file$it" to (it.mod(2) == 0) }.toMap()
    }

    @Before
    fun setup(){
        context = Any()

        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb").build()
        db.clearAllTables()

        //Add Nodes
        if(db.networkNodeDao.findAllActiveNodes().isEmpty()){
            db.networkNodeDao.insertList(defaultNetworkNodeList)
        }

        // Mock AvailabilityChecker
        availabilityChecker = mock {
            (1..5).forEach {
                onBlocking {
                    checkAvailability(eq(it.toLong()), any())
                }.thenReturn(AvailabilityCheckerResult(nodeAvailabilityMaps[it - 1], it.toLong()))
            }
        }

        onAvailabilityChanged = mock { }
        availabilityObserver = AvailabilityObserver(testOriginUrls, onAvailabilityChanged)

        availabilityManager = AvailabilityManager(db, availabilityChecker)
    }

    @Test
    fun givenNetworkNodesDiscoveredAndFilesRequested_thenRightInfoGottenFromDbAndObserverCalled(){


        GlobalScope.launch {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
            availabilityManager.runJob()
        }

        runBlocking {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }

        defaultNetworkNodeList.forEach { networkNode ->
            verify(onAvailabilityChanged, timeout(2000)).onAvailabilityChanged(argWhere {
                it.networkNodeUid == networkNode.networkNodeId
            })
        }

        argumentCaptor<AvailabilityEvent> {
            verify(onAvailabilityChanged, timeout(2000).times(defaultNetworkNodeList.size))
                .onAvailabilityChanged(capture())

            lastValue.originUrlsToAvailable.forEach { originUrlEntry ->
                Assert.assertEquals(nodeAvailabilityMaps.any { it.get(originUrlEntry.key) == true },
                    originUrlEntry.value)
            }
        }

    }

    fun givenActiveAvailabilityListener_whenNewNodeDiscovered_thenShouldSendRequestAndUpdate(){

    }

    fun givenActiveAvailabilityListener_whenNodeLost_thenShouldUpdateAvailability() {

    }

}