package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.lib.db.entities.AvailabilityObserverItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.dao.AvailabilityObserverItemDao
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.*

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

    private val mutableMapNode1: Map<String, Boolean> = mutableMapOf(
        "http://path.to/file1" to true,
        "http://path.to/file2" to false,
        "http://path.to/file3" to true,
        "http://path.to/file4" to false,
        "http://path.to/file5" to false,
    )
    private val mutableMapNode2: Map<String, Boolean> = mutableMapOf(
        "http://path.to/file1" to false,
        "http://path.to/file2" to true,
        "http://path.to/file3" to false,
        "http://path.to/file4" to true,
        "http://path.to/file5" to true,
    )
    private val mutableMapNode3: Map<String, Boolean> = mutableMapOf(
        "http://path.to/file1" to false,
        "http://path.to/file2" to true,
        "http://path.to/file3" to false,
        "http://path.to/file4" to false,
        "http://path.to/file5" to false,
    )
    private val mutableMapNode4: Map<String, Boolean> = mutableMapOf(
        "http://path.to/file1" to true,
        "http://path.to/file2" to false,
        "http://path.to/file3" to true,
        "http://path.to/file4" to false,
        "http://path.to/file5" to false,
    )
    private val mutableMapNode5: Map<String, Boolean> = mutableMapOf(
        "http://path.to/file1" to true,
        "http://path.to/file2" to false,
        "http://path.to/file3" to true,
        "http://path.to/file4" to false,
        "http://path.to/file5" to true,
    )

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
            onBlocking {
                checkAvailability(eq(1), any())
            }.thenReturn(AvailabilityCheckerResult(mutableMapNode1, 1))

            onBlocking {
                checkAvailability(eq(2), any())
            }.thenReturn(AvailabilityCheckerResult(mutableMapNode2, 2))

            onBlocking {
                checkAvailability(eq(3), any())
            }.thenReturn(AvailabilityCheckerResult(mutableMapNode3, 3))

            onBlocking {
                checkAvailability(eq(4), any())
            }.thenReturn(AvailabilityCheckerResult(mutableMapNode4, 4))

            onBlocking {
                checkAvailability(eq(5), any())
            }.thenReturn(AvailabilityCheckerResult(mutableMapNode5, 5))
        }

        availabilityManager = AvailabilityManager(db, availabilityChecker)

        availabilityObserver = mock<AvailabilityObserver>(defaultAnswer = CALLS_REAL_METHODS){
            on{urls2}.thenReturn(testOriginUrls)
        }
    }

//    @Test
//    fun givenNetworkNodesAroudAndFilesRequested_thenAvailabilityCheckerCalledForNodeAndOriginFile(){
//
//        db = spy(DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb").build())
//
//        availabilityObserverItemDaoSpy = spy(db.availabilityObserverItemDao)
//        whenever(db.availabilityObserverItemDao).thenReturn(availabilityObserverItemDaoSpy)
//        availabilityManager = AvailabilityManager(db, availabilityChecker)
//
//
//        GlobalScope.launch {
//            availabilityManager.addAvailabilityObserver(availabilityObserver)
//            availabilityManager.runJob()
//        }
//        verifyBlocking(availabilityObserverItemDaoSpy, timeout(2000)){
//            findPendingItems()
//        }
//
//    }

    @Test
    fun givenNetworkNodeAroundAndFilesRequested_thenRightInfoGottenFromDbAndObserverCalled(){


        GlobalScope.launch {
            availabilityManager.addAvailabilityObserver(availabilityObserver)
            availabilityManager.runJob()
        }

        verifyBlocking(availabilityObserver, timeout(5000).times(testOriginUrls.size)){
            onAvailabilityChanged(any())
        }
    }

}