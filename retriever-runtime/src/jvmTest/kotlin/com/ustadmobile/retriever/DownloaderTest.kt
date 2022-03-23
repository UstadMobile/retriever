package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.Retriever.Companion.STATUS_COMPLETE
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.*
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class DownloaderTest {

    private lateinit var db: RetrieverDatabase

    private lateinit var mockOriginServerFetcher: OriginServerFetcher

    private lateinit var mockAvailabilityManager: AvailabilityManager

    private lateinit var mockLocalPeerFetcher: LocalPeerFetcher

    private lateinit var mockProgressListener: RetrieverProgressListener

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

    @Test
    fun givenRequestNotAvailableLocally_whenDownloadCalled_thenShouldDownloadFromOriginServer() {
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
                    it.djiOriginUrl == downloadItem.djiOriginUrl &&
                            it.djiDestPath == downloadItem.djiDestPath
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
                val retrieverProgressListener = it.arguments[2] as RetrieverProgressListener
                val downloadJobItems = it.arguments[1] as List<DownloadJobItem>
                GlobalScope.launch {
                    downloadJobItems.forEach {
                        //send a complete event
                        retrieverProgressListener.onRetrieverProgress(RetrieverProgressEvent(it.djiUid, it.djiOriginUrl!!,
                            1000, 1000,0, 1000,
                            STATUS_COMPLETE))
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



}