package com.ustadmobile.retriever.util

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.ext.bindNewSqliteDataSourceIfNotExisting
import com.ustadmobile.door.ext.withDoorTransaction
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.db.entities.NetworkNode
import com.ustadmobile.retriever.db.entities.NetworkNodeFailure
import com.ustadmobile.retriever.db.entities.NetworkNodeSuccess
import com.ustadmobile.retriever.*
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.db.callback.NODE_STATUS_CHANGE_TRIGGER_CALLBACK
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import java.io.File
import javax.naming.InitialContext

class RetrieverTest {

    private lateinit var okHttpClient: OkHttpClient

    private lateinit var httpClient: HttpClient

    private lateinit var json: Json

    private lateinit var db: RetrieverDatabase

    private lateinit var mockAvailabilityManager: AvailabilityManager

    private lateinit var mockAvailabilityManagerFactory: AvailabilityManagerFactory

    private lateinit var mockAvailabilityChecker: AvailabilityChecker

    @JvmField
    @Rule
    var tempFolderRule = TemporaryFolder()

    var dbTimeStamp: Long = 0

    @Before
    fun setup() {
        Napier.takeLogarithm()
        Napier.base(DebugAntilog())

        dbTimeStamp = systemTimeInMillis()

        val sqliteDir = File(File("build"), "tmp")
        sqliteDir.takeIf { !it.exists() }?.mkdirs()
        InitialContext().bindNewSqliteDataSourceIfNotExisting("jvmTestDb_$dbTimeStamp",
            sqliteDir)
        Napier.d("TEST DATABASE: $dbTimeStamp")
        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "jvmTestDb_$dbTimeStamp")
            .addCallback(NODE_STATUS_CHANGE_TRIGGER_CALLBACK)
            .build()
        db.clearAllTables()

        okHttpClient = OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().also {
                it.maxRequests = 30
                it.maxRequestsPerHost = 10
            })
            .build()


        httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation)
            install(HttpTimeout)
            engine {
                preconfigured = okHttpClient
            }
        }

        json = Json {
            encodeDefaults = true
        }
        mockAvailabilityManager = mock { }
        mockAvailabilityManagerFactory = mock {
            on { makeAvailabilityManager(any(), any(), any(), any(), any(), any(), any()) }.thenReturn(mockAvailabilityManager)
        }
        mockAvailabilityChecker = mock { }
    }

    private fun RetrieverCommon.recordFailures(numFailure: Int, networkNodeId: Int) {
        runBlocking {
            for(i in 0 until numFailure) {
                db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                    this@recordFailures.handleNetworkNodeFailures(txDb, listOf(NetworkNodeFailure().apply {
                        failNetworkNodeId = networkNodeId
                        failTime = systemTimeInMillis()
                    }))
                }
            }
        }
    }


    fun RetrieverCommon.recordSuccess(numSuccess: Int, networkNodeId: Int) {
        runBlocking {
            for(i in 0 until numSuccess) {
                db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                    this@recordSuccess.handleNetworkNodeSuccessful(txDb,
                        listOf(NetworkNodeSuccess(networkNodeId, systemTimeInMillis())))
                }
            }
        }
    }

    fun RetrieverCommon.discoverNode(endpointUrl: String) : Int {
        return runBlocking {
            handleNodeDiscovered(NetworkNode().apply {
                networkNodeEndpointUrl = endpointUrl
            })

            db.networkNodeDao.findUidByEndpointUrl(endpointUrl)
        }
    }



    @Test
    fun givenNodeDiscovered_whenNodeFailsByMaxFailsAllowed_thenShouldStrikeOff() {
        val retrieverConfig = RetrieverConfig("ustad", 10000, 3)
        val retrieverJvm = RetrieverJvm(db, retrieverConfig,  mockAvailabilityChecker,  mock { }, mock {  },
            json, mockAvailabilityManagerFactory, mock {  }, GlobalScope)
        val nodeEndpoint = "http://192.1.68.1.123:34000/"

        val networkNodeId = retrieverJvm.discoverNode(nodeEndpoint)

        retrieverJvm.recordFailures(3, networkNodeId)

        verifyBlocking(mockAvailabilityManager) {
            handleNodesStruckOff(argWhere { networkNodeId in it })
        }

        val nodeInDb = runBlocking { db.networkNodeDao.findByUidAsync(networkNodeId) }
        Assert.assertEquals("Node status is now struck off", NetworkNode.STATUS_STRUCK_OFF,
            nodeInDb?.networkNodeStatus)
        retrieverJvm.close()
    }

    @Test
    fun givenNodeDiscoveredThenFails_whenNodeIsSuccessfulAgain_thenShouldBeRestored() {
        //Given - Setup and record failures
        val retrieverConfig = RetrieverConfig("ustad", 1000, 3)
        val retrieverJvm = RetrieverJvm(db, retrieverConfig,  mockAvailabilityChecker,  mock { }, mock {  },
            json,  mockAvailabilityManagerFactory, mock {  }, GlobalScope)
        retrieverJvm.start()
        mockAvailabilityManager.stub {
            on { checkQueue()} .thenAnswer {
                println("check available")
            }
        }
        val nodeEndpoint = "http://192.1.68.1.123:34000/"

        val networkNodeId = retrieverJvm.discoverNode(nodeEndpoint)
        retrieverJvm.recordFailures(3, networkNodeId)
        verifyBlocking(mockAvailabilityManager, timeout(1000)) {
            handleNodesStruckOff(argWhere { networkNodeId in it })
        }

        Assert.assertEquals("NetworkNode status is struck off", NetworkNode.STATUS_STRUCK_OFF,
            runBlocking {
                db.networkNodeDao.findByUidAsync(networkNodeId)?.networkNodeStatus
            })

        //When - now record the node coming back online
        Thread.sleep(500)

        retrieverJvm.recordSuccess(1, networkNodeId)

        verify(mockAvailabilityManager, timeout(2000)).checkQueue(true)

        val nodeInDb = runBlocking { db.networkNodeDao.findByUidAsync(networkNodeId) }
        Assert.assertEquals("NetworkNode status is OK", NetworkNode.STATUS_OK,
            nodeInDb?.networkNodeStatus)

        //Should be called once when Retriever start() is called, once when discovered, and then again when restored

        retrieverJvm.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun givenInvalidIntegrity_whenRetrieveCalled_thenShouldThrowIllegalArgumentException() {
        val retrieverConfig = RetrieverConfig("ustad", 1000, 3)
        val retrieverJvm = RetrieverJvm(db, retrieverConfig,  mockAvailabilityChecker,  mock { }, mock {  },
            json,  mockAvailabilityManagerFactory, mock {  }, GlobalScope)

        runBlocking {
            retrieverJvm.retrieve(listOf(RetrieverRequest("http://server.com/file",
                tempFolderRule.newFile().absolutePath, "foobar")), mock { })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun givenIntegrityNotInConfiguration_whenRetrieveCalled_thenShouldThrowIllegalArgumentException() {
        val retrieverConfig = RetrieverConfig("ustad", 1000, 3,
            integrityChecksumTypes = arrayOf(IntegrityChecksum.SHA256))
        val retrieverJvm = RetrieverJvm(db, retrieverConfig,  mockAvailabilityChecker,  mock { }, mock {  },
            json,  mockAvailabilityManagerFactory, mock {  }, GlobalScope)

        runBlocking {
            retrieverJvm.retrieve(listOf(RetrieverRequest("http://server.com/file",
                tempFolderRule.newFile().absolutePath,
                "sha384-oqVuAfXRKap7fdgcCY5uykM6+R9GqQ8K/uxy9rx7HNQlGYl1kPzQho1wx4JwY8wC")), mock { })
        }
        retrieverJvm.close()
    }

    @Test
    fun givenNodeDiscovered_whenNodeIsLostAndStruckOff_thenShouldBeDeleted() {

    }



}