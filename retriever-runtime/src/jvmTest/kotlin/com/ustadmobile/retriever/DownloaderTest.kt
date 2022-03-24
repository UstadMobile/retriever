package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_SUCCESSFUL
import com.ustadmobile.retriever.Retriever.Companion.STATUS_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.*
import com.ustadmobile.retriever.fetcher.RetrieverListener
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import java.io.IOException

class DownloaderTest {

    private lateinit var db: RetrieverDatabase

    private lateinit var mockOriginServerFetcher: OriginServerFetcher

    private lateinit var mockAvailabilityManager: AvailabilityManager

    private lateinit var mockLocalPeerFetcher: LocalPeerFetcher

    private lateinit var mockProgressListener: RetrieverListener

    private lateinit var downloadJobItems: List<DownloadJobItem>

    @Before
    fun setup() {
        Napier.base(DebugAntilog())
        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }

        mockOriginServerFetcher = mock {

        }

        mockLocalPeerFetcher = mock {

        }

        mockAvailabilityManager = mock {

        }

        mockProgressListener = mock {

        }

        downloadJobItems = (1..2).map { index ->
            DownloadJobItem().apply {
                djiBatchId = 42
                djiOriginUrl = "http://server.com/file$index.zip"
                djiDestPath = "file://folder/file$index.zip"
                djiStatus = STATUS_QUEUED
            }
        }

        runBlocking {
            db.downloadJobItemDao.insertList(downloadJobItems)
        }
    }

    private fun KStubbing<OriginServerFetcher>.onOriginDownloadThenAnswerAndFireUpdate(
        statusBlock: (InvocationOnMock) -> Int
    ) = onBlocking {
        download(any(), any())
    }.thenAnswer {
        val downloadJobItem = it.arguments[0] as DownloadJobItem
        val retrieverListener = it.arguments[1] as RetrieverListener
        GlobalScope.launch {
            retrieverListener.onRetrieverProgress(RetrieverProgressEvent(downloadJobItem.djiUid,
                downloadJobItem.djiOriginUrl!!, 1000, 0, 1000, 1000))
            retrieverListener.onRetrieverStatusUpdate(
                RetrieverStatusUpdateEvent(downloadJobItem.djiUid,
                downloadJobItem.djiOriginUrl!!, statusBlock(it))
            )
        }
    }

    @Test
    fun givenRequestNotAvailableLocally_whenDownloadCalled_thenShouldDownloadFromOriginServer() {
        mockOriginServerFetcher.stub {
            onOriginDownloadThenAnswerAndFireUpdate { STATUS_SUCCESSFUL }
        }

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockOriginServerFetcher, mockLocalPeerFetcher, db)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        downloadJobItems.forEach { downloadItem ->
            verifyBlocking(mockOriginServerFetcher) {
                download(argWhere {
                    it.djiOriginUrl == downloadItem.djiOriginUrl && it.djiDestPath == downloadItem.djiDestPath
                }, any())
            }
        }
    }

    @Test
    fun givenRequestAvailableLocally_whenDownloadCalled_thenShouldDownloadFromPeer() {
        val networkNode = NetworkNode().apply {
            networkNodeEndpointUrl = "http://192.168.0.4:12131/retriever/"
            networkNodeId = db.networkNodeDao.insert(this)
        }

        db.availabilityResponseDao.insertList(downloadJobItems.map {
            AvailabilityResponse(networkNode.networkNodeId, it.djiOriginUrl!!, true, systemTimeInMillis())
        })

        mockLocalPeerFetcher.stub {
            onBlocking { download(any(), any(), any()) }.thenAnswer {
                val retrieverListener = it.arguments[2] as RetrieverListener
                val downloadJobItems = it.arguments[1] as List<DownloadJobItem>
                GlobalScope.launch {
                    downloadJobItems.forEach {
                        //send a complete event
                        retrieverListener.onRetrieverProgress(RetrieverProgressEvent(it.djiUid, it.djiOriginUrl!!,
                            1000, 1000,0, 1000))
                        retrieverListener.onRetrieverStatusUpdate(RetrieverStatusUpdateEvent(it.djiUid,
                            it.djiOriginUrl!!, STATUS_SUCCESSFUL))
                    }
                }


                FetchResult()
            }
        }

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockOriginServerFetcher, mockLocalPeerFetcher, db)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }


        verifyBlocking(mockLocalPeerFetcher) {
            download(eq(networkNode.networkNodeEndpointUrl!!), argWhere { listArg ->
                downloadJobItems.all { item ->  listArg.any { it.djiOriginUrl == item.djiOriginUrl } }
            }, any())
        }
    }

    @Test
    fun givenServerFailsRepeatedly_whenDownloadCalled_thenShouldFail() {
        val maxNumAttempts = 4

        mockOriginServerFetcher.stub {
            onOriginDownloadThenAnswerAndFireUpdate { STATUS_ATTEMPT_FAILED }
        }

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockOriginServerFetcher, mockLocalPeerFetcher, db, maxAttempts = maxNumAttempts, attemptRetryDelay = 100)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        downloadJobItems.forEach { downloadItem ->
            verifyBlocking(mockOriginServerFetcher, times(maxNumAttempts)) {
                download(argWhere {
                    it.djiOriginUrl == downloadItem.djiOriginUrl && it.djiDestPath == downloadItem.djiDestPath
                }, any())
            }
        }

        downloadJobItems.forEach { jobItem ->
            verifyBlocking(mockProgressListener) {
                onRetrieverStatusUpdate(argWhere {
                    it.url == jobItem.djiOriginUrl && it.status == STATUS_FAILED
                })
            }
        }
    }


    @Test
    fun givenServerFailsOnce_whenDownloadCalled_thenShouldRetry() {
        val maxNumAttempts = 4
        val timesToFail = 2
        var failCount = timesToFail

        //Make the first item fail twice, then succeed
        mockOriginServerFetcher.stub {
            onOriginDownloadThenAnswerAndFireUpdate {
                val jobItem = it.arguments.first() as DownloadJobItem
                if(jobItem.djiOriginUrl == downloadJobItems.first().djiOriginUrl) {
                    failCount--
                    if(failCount >= 0)
                        STATUS_ATTEMPT_FAILED
                    else
                        STATUS_SUCCESSFUL
                }else {
                    STATUS_SUCCESSFUL
                }
            }
        }

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockOriginServerFetcher, mockLocalPeerFetcher, db, maxAttempts = maxNumAttempts, attemptRetryDelay = 100)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        verifyBlocking(mockOriginServerFetcher, times(timesToFail + 1)) {
            download(argWhere { it.djiOriginUrl == downloadJobItems.first().djiOriginUrl }, any())
        }

        downloadJobItems.forEach { downloadItem ->
            verifyBlocking(mockOriginServerFetcher, atLeastOnce()) {
                download(argWhere {
                    it.djiOriginUrl == downloadItem.djiOriginUrl && it.djiDestPath == downloadItem.djiDestPath
                }, any())
            }

            verifyBlocking(mockProgressListener) {
                onRetrieverStatusUpdate(argWhere {
                    it.url == downloadItem.djiOriginUrl && it.status == STATUS_SUCCESSFUL
                })
            }
        }
    }

    /**
     * Test what will happen when a downloader does not fire a status change event (e.g. because it threw an exception)
     */
    @Test
    fun givenServerFailsInMiddle_whenDownloadCalled_thenShouldSetStatusAndRetry() {
        val maxNumAttempts = 4
        val timesToFail = 2
        var failCount = timesToFail

        //Make the first item fail twice, then succeed
        mockOriginServerFetcher.stub {
            onBlocking { download(any(), any()) }.thenAnswer {
                val downloadJobItem = it.arguments[0] as DownloadJobItem
                val retrieverListener = it.arguments[1] as RetrieverListener

                if(failCount-- >= 0) {
                    runBlocking {
                        retrieverListener.onRetrieverProgress(RetrieverProgressEvent(
                            downloadJobItem.djiUid, downloadJobItem.djiOriginUrl!!, 0, 0,
                            0, 1000))
                    }

                    throw IOException("Fail!")
                }else {
                    runBlocking {
                        retrieverListener.onRetrieverProgress(RetrieverProgressEvent(downloadJobItem.djiUid,
                            downloadJobItem.djiOriginUrl!!, 1000, 0, 1000, 1000))
                        retrieverListener.onRetrieverStatusUpdate(
                            RetrieverStatusUpdateEvent(downloadJobItem.djiUid,
                                downloadJobItem.djiOriginUrl!!, STATUS_SUCCESSFUL)
                        )
                    }
                }
            }
        }

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockOriginServerFetcher, mockLocalPeerFetcher, db, maxAttempts = maxNumAttempts, attemptRetryDelay = 100)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        downloadJobItems.forEach { downloadItem ->
            verifyBlocking(mockOriginServerFetcher, atLeastOnce()) {
                download(argWhere {
                    it.djiOriginUrl == downloadItem.djiOriginUrl && it.djiDestPath == downloadItem.djiDestPath
                }, any())
            }

            verifyBlocking(mockProgressListener) {
                onRetrieverStatusUpdate(argWhere {
                    it.url == downloadItem.djiOriginUrl && it.status == STATUS_SUCCESSFUL
                })
            }
        }


    }

}