package com.ustadmobile.retriever

import com.ustadmobile.lib.db.entities.LocallyStoredFile
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.ext.crc32
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import com.ustadmobile.retriever.responder.AvailabilityResponder
import com.ustadmobile.retriever.responder.ZippedItemsResponder
import fi.iki.elonen.router.RouterNanoHTTPD
import java.io.File
import kotlinx.serialization.json.Json


abstract class RetrieverCommonJvm(
    db: RetrieverDatabase,
    nsdServiceName: String,
    availabilityChecker: AvailabilityChecker,
    originServerFetcher: OriginServerFetcher,
    localPeerFetcher: LocalPeerFetcher,
    protected val json: Json,
): RetrieverCommon(db, nsdServiceName, availabilityChecker, originServerFetcher, localPeerFetcher) {

    internal val server = RouterNanoHTTPD(0)

    init {
        server.addRoute("/:${AvailabilityResponder.PARAM_FILE_REQUEST_URL}/", AvailabilityResponder::class.java,
            db, json)
        server.addRoute("/zipped", ZippedItemsResponder::class.java, db)
        server.start()
    }

    override suspend fun addFiles(files: List<LocalFileInfo>) {
        val locallyStoredFiles = files.map {
            val file = File(it.filePath)
            if(!file.exists())
                throw IllegalArgumentException("addFiles: ${file.absolutePath} does not exist!")

            LocallyStoredFile(it.originUrl, it.filePath, file.length(), file.crc32)
        }

        db.locallyStoredFileDao.insertList(locallyStoredFiles)
    }

    override fun close() {
        super.close()
        server.stop()
    }
}