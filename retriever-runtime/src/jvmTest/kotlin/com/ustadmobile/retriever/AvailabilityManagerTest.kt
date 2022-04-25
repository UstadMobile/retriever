package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.lib.db.entities.NetworkNodeFailure
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import com.ustadmobile.retriever.util.mockRecordingFailuresAndNodeStrikeOff
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.IOException

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
        NetworkNode("http://100.1.1.1:8081/", DateTime.nowUnixLong(), 1),
        NetworkNode("http://100.1.1.2:8081/", DateTime.nowUnixLong(), 2),
        NetworkNode("http://100.1.1.3:8081/", DateTime.nowUnixLong(), 3),
        NetworkNode("http://100.1.1.4:8081/", DateTime.nowUnixLong(), 4),
        NetworkNode("http://100.1.1.5:8081/", DateTime.nowUnixLong(), 5)
    )

    /**
     * the default mock will throw an exception if the endpoint is in this list
     */
    private var failingEndpoints = mutableListOf<String>()

    /**
     * List of maps of what is available on each node. One file is on each node
     */
    private val nodeAvailabilityResponses: List<List<FileAvailableResponse>> = (0..4).map { nodeIndex ->
        listOf(FileAvailableResponse("http://path.to/file${nodeIndex + 1}", "ab", 42))
    }

    private lateinit var mockNodeHandler: RetrieverNodeHandler

    @Before
    fun setup(){
        context = Any()

        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb")
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build()
        db.clearAllTables()

        db.networkNodeDao.insertList(defaultNetworkNodeList)

        availabilityChecker = mock {
            (0..4).forEach { networkNodeIndex ->
                onBlocking {
                    checkAvailability(
                        argThat{ networkNodeId == defaultNetworkNodeList[networkNodeIndex].networkNodeId } ,
                        any())
                }.thenAnswer {
                    if(defaultNetworkNodeList[networkNodeIndex].networkNodeEndpointUrl in failingEndpoints)
                        throw IOException("Node $networkNodeIndex fails!")

                    AvailabilityCheckerResult(
                        nodeAvailabilityResponses[networkNodeIndex], defaultNetworkNodeList[networkNodeIndex].networkNodeId)
                }
            }
        }

        onAvailabilityChanged = mock { }
        mockNodeHandler = mock { }
        availabilityObserver = AvailabilityObserver(testOriginUrls, onAvailabilityChanged)

        availabilityManager = AvailabilityManager(db, availabilityChecker, retryDelay = 200,
            nodeHandler = mockNodeHandler)
    }

    fun tearDown() {
        availabilityManager.close()
    }

    @Test
    fun givenNetworkNodesDiscovered_whenSummaryAvailabilityObserverAdded_thenShouldCheckStatusAndReturnMatchingInfo(){

        //Add to the watch list and call runJob
        runBlocking {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }


        //Should run availability checker for each node
        defaultNetworkNodeList.forEach { networkNode ->
            verifyBlocking(availabilityChecker, timeout(1000)) {
                checkAvailability(
                    argWhere { it.networkNodeEndpointUrl == networkNode.networkNodeEndpointUrl },
                    argWhere { it.containsAll(testOriginUrls) })
            }
        }


        //Verify for every node, onAvailabilityChanged was called
        defaultNetworkNodeList.forEach { networkNode ->
            verify(onAvailabilityChanged, timeout(2000)).onAvailabilityChanged(argWhere {
                it.networkNodeUid == networkNode.networkNodeId
            })
        }

        //Verify for onAvailabilityChanged event was called for every Node and its result matches
        // pre determined nodeAvailabilityMaps as defined.
        argumentCaptor<AvailabilityEvent> {

            //Expected number of calls is once when first added, then again after each receiving each response
            verify(onAvailabilityChanged, timeout(2000).times(defaultNetworkNodeList.size + 1))
                .onAvailabilityChanged(capture())

            lastValue.originUrlsToAvailable.forEach { originUrlEntry ->
                Assert.assertEquals(nodeAvailabilityResponses.flatten().any { it.originUrl == originUrlEntry.key },
                    originUrlEntry.value)
            }

            Assert.assertFalse("After all checks made, no checks are pending",
                lastValue.checksPending)
        }

    }

    @Test
    fun givenNetworkNodesDiscovered_whenDetailedAvailabilityObserverAdded_thenShouldCheckStatusAndReturnMatchingInfo() {
        availabilityObserver = AvailabilityObserver(testOriginUrls, onAvailabilityChanged,
            AvailabilityObserverItem.MODE_INC_AVAILABLE_NODES)

        runBlocking {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }

        argumentCaptor<AvailabilityEvent> {
            //Expected number of calls is once when first added, then again after each receiving each response
            verify(onAvailabilityChanged, timeout(2000).times(defaultNetworkNodeList.size + 1))
                .onAvailabilityChanged(capture())

            testOriginUrls.forEach { originUrl ->
                runBlocking {
                    val expectedEndpointsForOriginUrl = defaultNetworkNodeList.filterIndexed { index, networkNode ->
                        availabilityChecker.checkAvailability(networkNode, listOf(originUrl)).hasOriginUrl(originUrl)
                    }.map { it.networkNodeEndpointUrl }

                    val hasAllExpectedEndpoints = lastValue.availabilityInfo[originUrl]?.availableEndpoints
                        ?.containsAll(expectedEndpointsForOriginUrl)
                    Assert.assertTrue("Final result lists all nodes expected to have this url",
                          hasAllExpectedEndpoints ?: false)
                }
            }
        }

        verify(onAvailabilityChanged, timeout(2000).atLeastOnce()).onAvailabilityChanged(argWhere {
            !it.checksPending
        })
    }


    @Test
    fun givenActiveAvailabilityListener_whenNodeStruckOff_thenShouldUpdateAvailability() {
        availabilityObserver = AvailabilityObserver(testOriginUrls, onAvailabilityChanged,
            AvailabilityObserverItem.MODE_INC_AVAILABLE_NODES)

        runBlocking {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }

        verify(onAvailabilityChanged, timeout(2000).times(defaultNetworkNodeList.size + 1))
            .onAvailabilityChanged(any())

        //Record failures so the node is considered struck off
        val failStartTime = systemTimeInMillis()
        runBlocking {

            db.networkNodeFailureDao.insertListAsync((0..3).map {
                NetworkNodeFailure().apply {
                    failTime = systemTimeInMillis()
                    failNetworkNodeId = defaultNetworkNodeList.first().networkNodeId
                }
            })
            db.networkNodeDao.strikeOffNodes(systemTimeInMillis() - 30000, 3, failStartTime)
        }

        runBlocking {
            availabilityManager.handleNodesStruckOff(listOf(defaultNetworkNodeList.first().networkNodeId))
        }

        argumentCaptor<AvailabilityEvent> {
            //Will be called once immediately when the osbserver is added, once for each network node as the availability
            // check comes in, and then once more when the a node is recored as struck off.
            verify(onAvailabilityChanged, timeout(5000).times(defaultNetworkNodeList.size + 2))
                .onAvailabilityChanged(capture())
            Assert.assertFalse("First file is not available after first node is lost",
                lastValue.availabilityInfo[testOriginUrls.first()]!!.available)
        }

    }

    @Test
    fun givenActiveAvailabilityListener_whenAvailabilityCheckerFailsRepeatedlyOnOneNode_thenShouldRetryRecordFailuresAndGiveUp() {
        availabilityObserver = AvailabilityObserver(testOriginUrls, onAvailabilityChanged,
            AvailabilityObserverItem.MODE_INC_AVAILABLE_NODES)

        mockNodeHandler.mockRecordingFailuresAndNodeStrikeOff {
            runBlocking { availabilityManager.handleNodesStruckOff(it) }
        }

        val endpointToFail = defaultNetworkNodeList[0].networkNodeEndpointUrl!!
        failingEndpoints += endpointToFail

        runBlocking {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
        }

        runBlocking {
            delay(2000) //Wait to be sure that we don't have repeated attempts to check what has failed going on
        }

        verifyBlocking(availabilityChecker, times(3)) { //3 attempts where 3 failures are allowed
            checkAvailability(argWhere {
                it.networkNodeEndpointUrl == endpointToFail
            }, any())
        }

        //Should report the failures to the node handler
        verifyBlocking(mockNodeHandler, times(3)) {
            handleNetworkNodeFailures(any(), argWhere { failList ->
                failList.any { it.failNetworkNodeId == defaultNetworkNodeList[0].networkNodeId }
            })
        }

        verify(onAvailabilityChanged, timeout(5000)).onAvailabilityChanged(argWhere {
            !it.checksPending
        })
    }


}