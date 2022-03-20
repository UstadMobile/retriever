package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.crc32
import com.ustadmobile.retriever.fetcher.MultiItemFetcher
import com.ustadmobile.retriever.fetcher.SingleItemFetcher
import java.io.File

abstract class RetrieverCommonJvm(
    db: RetrieverDatabase,
    nsdServiceName: String,
    availabilityChecker: AvailabilityChecker,
    singleItemFetcher: SingleItemFetcher,
    multiItemFetcher: MultiItemFetcher,
): RetrieverCommon(db, nsdServiceName, availabilityChecker, singleItemFetcher, multiItemFetcher) {

    override suspend fun addFiles(files: List<LocalFileInfo>) {
        val locallyStoredFiles = files.map {
            val file = File(it.filePath)
            if(!file.exists())
                throw IllegalArgumentException("addFiles: ${file.absolutePath} does not exist!")

            LocallyStoredFile(it.originUrl, it.filePath, file.length(), file.crc32)
        }

        db.locallyStoredFileDao.insertList(locallyStoredFiles)
    }
}