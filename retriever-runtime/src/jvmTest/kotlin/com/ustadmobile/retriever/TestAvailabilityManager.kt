package com.ustadmobile.retriever

import com.soywiz.klock.DateTime
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * JVM Test
 */
class TestAvailabilityManager {


    private lateinit var db: RetrieverDatabase

    private lateinit var context: Any

    private val defaultNetworkNodeList: List<NetworkNode> = listOf(
        NetworkNode("100.1.1.1", "100.1.1.1:8081/", DateTime.nowUnixLong()),
        NetworkNode("100.1.1.2", "100.1.1.2:8081/", DateTime.nowUnixLong()),
        NetworkNode("100.1.1.3", "100.1.1.3:8081/", DateTime.nowUnixLong()),
        NetworkNode("100.1.1.4", "100.1.1.4:8081/", DateTime.nowUnixLong()),
        NetworkNode("100.1.1.5", "100.1.1.5:8081/", DateTime.nowUnixLong())
    )

    @Before
    fun setup(){
        context = Any()

        db = DatabaseBuilder.databaseBuilder(context, RetrieverDatabase::class,"jvmTestDb").build()

        //1. Add Nodes

        //2. Add Availability Observer Item with Observer id


    }

    @Test
    fun givenNetworkNodesAroudAndFilesRequested_thenAvailabilityCheckerCalledForNodeAndOriginFile(){


        Assert.assertEquals(1, 1)

    }

    @Test
    fun givenNetworkNodeAroundAndFilesRequested_thenRightInfoGottenFromDbAndObserverCalled(){
        Assert.assertEquals(2, 1)
    }

}