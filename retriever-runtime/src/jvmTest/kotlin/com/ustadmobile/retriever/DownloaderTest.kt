package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.AvailabilityResponse
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.NetworkNode
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.*
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class DownloaderTest {

    private lateinit var db: RetrieverDatabase

    private lateinit var mockSingleItemFetcher: SingleItemFetcher

    private lateinit var mockAvailabilityManager: AvailabilityManager

    private lateinit var mockMultiItemFetcher: MultiItemFetcher

    private lateinit var mockProgressListener: ProgressListener

    private lateinit var downloadJobItems: List<DownloadJobItem>

    @Before
    fun setup() {
        Napier.base(DebugAntilog())
        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }

        mockSingleItemFetcher = mock {

        }

        mockMultiItemFetcher = mock {

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
                djiStatus = DownloadJobItem.STATUS_QUEUED
            }
        }

        runBlocking {
            db.downloadJobItemDao.insertList(downloadJobItems)
        }
    }

    @Test
    fun givenRequestNotAvailableLocally_whenDownloadCalled_thenShouldDownloadFromOriginServer() {
        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockSingleItemFetcher, mockMultiItemFetcher, db)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }

        downloadJobItems.forEach { downloadItem ->
            verifyBlocking(mockSingleItemFetcher) {
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

        mockMultiItemFetcher.stub {
            onBlocking { download(any(), any(), any()) }.thenAnswer {
                val fetchProgressListener = it.arguments[2] as FetchProgressListener
                val downloadJobItems = it.arguments[1] as List<DownloadJobItem>
                downloadJobItems.forEach {
                    //send a complete event
                    fetchProgressListener.onFetchProgress(FetchProgressEvent(it.djiUid, 1000, 1000))
                }

                FetchResult(200)
            }
        }

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockSingleItemFetcher, mockMultiItemFetcher, db)

        runBlocking {
            withTimeout(10000) {
                downloader.download()
            }
        }


        verifyBlocking(mockMultiItemFetcher) {
            download(eq(networkNode.networkNodeEndpointUrl!!), argWhere { listArg ->
                downloadJobItems.all { item ->  listArg.any { it.djiOriginUrl == item.djiOriginUrl } }
            }, any())
        }
    }



}