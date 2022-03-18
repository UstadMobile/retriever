package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * JVM Test
 */
class AvailabilityManagerTest {

    private lateinit var db: RetrieverDatabase

    private lateinit var availabilityChecker: AvailabilityChecker

    private lateinit var availabilityManager: AvailabilityManager

    private lateinit var context: Any

    private lateinit var availabilityObserver: AvailabilityObserver

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

        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb")
            .build()
        db.clearAllTables()

        //Add Nodes
        db.networkNodeDao.insertList(defaultNetworkNodeList)

        availabilityChecker = mock {
            (0..4).forEach {
                onBlocking {
                    checkAvailability(
                        argThat{ networkNodeId == defaultNetworkNodeList[it].networkNodeId} ,
                        any())
                }.thenReturn(AvailabilityCheckerResult(
                    nodeAvailabilityMaps[it], defaultNetworkNodeList[it].networkNodeId))
            }
        }

        onAvailabilityChanged = mock { }
        availabilityObserver = AvailabilityObserver(testOriginUrls, onAvailabilityChanged)

        availabilityManager = AvailabilityManager(db, availabilityChecker)
    }

    fun tearDown() {
        availabilityManager.close()
    }

    @Test
    fun givenNetworkNodesDiscoveredAndFilesRequested_thenRightInfoGottenFromDbAndObserverCalled(){

        //Add to the watch list and call runJob
        runBlocking {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }



        //Verify for every node, onAvailabilityChanged was called
        defaultNetworkNodeList.forEach { networkNode ->
            verify(onAvailabilityChanged, timeout(2000))
                .onAvailabilityChanged(argWhere {
                it.networkNodeUid == networkNode.networkNodeId
            })
        }


        defaultNetworkNodeList.forEach { networkNode ->
            verifyBlocking(availabilityChecker, timeout(1000)) {
                checkAvailability(
                    argWhere { it.networkNodeEndpointUrl == networkNode.networkNodeEndpointUrl },
                    argWhere { it.containsAll(testOriginUrls) })
            }
        }

        //Verify for onAvailabilityChanged event was called for every Node and its result matches
        // pre determined nodeAvailabilityMaps as defined.
        argumentCaptor<AvailabilityEvent> {
            verify(onAvailabilityChanged, timeout(2000).times(defaultNetworkNodeList.size))
                .onAvailabilityChanged(capture())

            lastValue.originUrlsToAvailable.forEach { originUrlEntry ->
                Assert.assertEquals(nodeAvailabilityMaps.any { it[originUrlEntry.key] == true },
                    originUrlEntry.value)
            }
        }

    }

    fun givenActiveAvailabilityListener_whenNewNodeDiscovered_thenShouldSendRequestAndUpdate(){

    }

    fun givenActiveAvailabilityListener_whenNodeLost_thenShouldUpdateAvailability() {

    }

}