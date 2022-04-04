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
import com.ustadmobile.door.ext.withDoorTransactionAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

abstract class RetrieverCommonJvm(
    db: RetrieverDatabase,
    nsdServiceName: String,
    availabilityChecker: AvailabilityChecker,
    originServerFetcher: OriginServerFetcher,
    localPeerFetcher: LocalPeerFetcher,
    port: Int,
    protected val json: Json,
    retrieverCoroutineScope: CoroutineScope,
): RetrieverCommon(
    db, nsdServiceName, availabilityChecker, originServerFetcher, localPeerFetcher, port, retrieverCoroutineScope
) {

    private val serverCompletable = CompletableDeferred<RouterNanoHTTPD>()

    @Volatile
    private var runningServer: RouterNanoHTTPD? = null

    internal open fun start() {
        retrieverCoroutineScope.launch(Dispatchers.IO) {
            val chosenPort = choosePort()
            val nanoHttpdServer = RouterNanoHTTPD(chosenPort)
            nanoHttpdServer.addRoute("/:${AvailabilityResponder.PARAM_FILE_REQUEST_URL}/", AvailabilityResponder::class.java,
                db, json)
            nanoHttpdServer.addRoute("/zipped", ZippedItemsResponder::class.java, db)
            nanoHttpdServer.start()
            runningServer = nanoHttpdServer
            serverCompletable.complete(nanoHttpdServer)
        }
    }


    /**
     * Use logic on the underlying platform (e.g. settings storage on Android) to determine the port to start on
     */
    protected abstract suspend fun choosePort(): Int

    override suspend fun addFiles(files: List<LocalFileInfo>) {
        val locallyStoredFiles = files.map {
            val file = File(it.filePath)
            if(!file.exists())
                throw IllegalArgumentException("addFiles: ${file.absolutePath} does not exist!")

            LocallyStoredFile(it.originUrl, it.filePath, file.length(), file.crc32)
        }

        db.locallyStoredFileDao.insertList(locallyStoredFiles)
    }

    override suspend fun getAllLocallyStoredFiles(): List<LocallyStoredFile>{
        return db.locallyStoredFileDao.findAllLocallyStoredFiles()
    }


    override suspend fun getLocallyStoredFilesByUrls(urls: List<String>): List<LocallyStoredFile> {
        return db.locallyStoredFileDao.findLocallyStoredFilesByUrlList(urls)
    }

    override suspend fun deleteFilesByUrl(urls: List<String>) {
        //TODO: Chunk this into batches of 100
        db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
            val locallyStoredFiles = txDb.locallyStoredFileDao.findLocallyStoredFilesByUrlList(urls)
            locallyStoredFiles.forEach { storedFile ->
                storedFile.lsfFilePath?.let { File(it) }?.delete()
                txDb.locallyStoredFileDao.removeFile(storedFile.locallyStoredFileUid)
            }
            txDb.downloadJobItemDao.deleteByUrlList(urls)
        }
    }

    override suspend fun listeningPort(): Int {
        return serverCompletable.await().listeningPort
    }

    internal suspend fun awaitServer(): RouterNanoHTTPD {
        return serverCompletable.await()
    }

    override fun close() {
        super.close()
        runningServer?.stop()
    }
}