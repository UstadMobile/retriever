package com.ustadmobile.retriever

import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DoorUri
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.SingleItemFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class DownloaderTest {

    private lateinit var db: RetrieverDatabase

    private lateinit var mockSingleItemFetcher: SingleItemFetcher

    private lateinit var mockAvailabilityManager: AvailabilityManager

    private lateinit var mockProgressListener: ProgressListener

    @Before
    fun setup() {
        db = DatabaseBuilder.databaseBuilder(Any(), RetrieverDatabase::class, "RetrieverDatabase")
            .build().also {
                it.clearAllTables()
            }

        mockSingleItemFetcher = mock {

        }

        mockAvailabilityManager = mock {

        }

        mockProgressListener = mock {

        }
    }

    @Test
    fun givenRequestNotAvailableLocally_whenDownloadCalled_thenShouldDownloadFromOriginServer() {
        val downloadJobItems = (1..2).map { index ->
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

        val downloader = Downloader(42, mockAvailabilityManager, mockProgressListener,
            mockSingleItemFetcher, db)

        runBlocking {
            downloader.download()
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


}