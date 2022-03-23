    package com.ustadmobile.retriever

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_COMPLETE
import com.ustadmobile.retriever.Retriever.Companion.STATUS_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverProgressListener
import com.ustadmobile.retriever.fetcher.OriginServerFetcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlin.math.min
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Download approach:
 *   Put all download items into the database for the request
 *   Wait until discovery of potential mirrors is done, at most 1-2 seconds (if nodes are available).
 *   Use produce-consume channel - run a query to find next downloads (look to find the mirror with the most files
 *   available, and bundle), otherwise,
 *   download individually from the original source.
 *
 *
 * fun produceItems
 *   batch requests together, put them on a channel, continue until all items are finished
 */
class Downloader(
    private val downloadBatchId: Long,
    private val availabilityManager: AvailabilityManager,
    private val progressListener: RetrieverProgressListener,
    private val originServerFetcher: OriginServerFetcher,
    private val localPeerFetcher: LocalPeerFetcher,
    private val db: RetrieverDatabase,
    private val maxConcurrent: Int = 8,
    private val maxAttempts: Int = 8,
    private val attemptRetryDelay: Int = 1000,
) {

    private val checkQueueChannel = Channel<Boolean>(capacity = Channel.UNLIMITED)

    private data class DownloadBatch(val host: String?, val itemsToDownload: List<DownloadJobItem>)

    private val activeBatches: MutableList<DownloadBatch> = concurrentSafeListOf()

    private val logPrefix = "[Retriever-Downloader #$downloadBatchId] "

    private val progressUpdateMutex = Mutex()

    private val pendingUpdates = concurrentSafeMapOf<Long, RetrieverProgressEvent>()

    /**
     * Produce items -
     *  Group into batches - whatever is available locally, take that first. Remaining (from Internet) items download
     *  individually
     */
    @ExperimentalCoroutinesApi
    private fun CoroutineScope.produceJobs() = produce<DownloadBatch> {
        var done = false
        try {
            do {
                checkQueueChannel.receive()
                Napier.d("$logPrefix: checking queue", tag = Retriever.LOGTAG)
                val numProcessorsAvailable = maxConcurrent - activeBatches.size
                val batchesToSend = db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                    val queueItems = txDb.downloadJobItemDao.findNextItemsToDownload(downloadBatchId)
                    //turn these into batches
                    val groupedByNode = queueItems.groupBy { it.networkNodeId }.toMutableMap()
                    val locallyAvailableBatches = groupedByNode.entries.filter { it.key != 0L }
                        .map {  entry ->
                            DownloadBatch(entry.value.first().networkNodeEndpointUrl, entry.value.sortedBy { it.djiIndex })
                        }
                    val locallyAvailableBatchesToSend = locallyAvailableBatches.subList(0,
                        min(numProcessorsAvailable, locallyAvailableBatches.size))

                    val numFromOriginToSend = min(groupedByNode[0]?.size ?: 0,
                        numProcessorsAvailable - locallyAvailableBatchesToSend.size)
                    val originDownloadBatchesToSend = groupedByNode[0]?.subList(0, numFromOriginToSend)
                        ?.map { DownloadBatch(null, listOf(it)) } ?: listOf()

                    val jobIdsSent = locallyAvailableBatchesToSend.flatMap { batch ->
                        batch.itemsToDownload.map { it.djiUid }
                    } + originDownloadBatchesToSend.flatMap { batch ->
                        batch.itemsToDownload.map { it.djiUid }
                    }

                    jobIdsSent.forEach {
                        txDb.downloadJobItemDao.updateStatusByUid(it, STATUS_RUNNING)
                    }

                    done = txDb.downloadJobItemDao.isBatchDone(downloadBatchId)

                    (locallyAvailableBatchesToSend + originDownloadBatchesToSend).also {
                        if(it.isNotEmpty())
                            Napier.d("$logPrefix starting download ids: ${it.joinToString()}")
                    }
                }

                batchesToSend.forEach {
                    send(it)
                }
            }while(!done)
        }finally {


        }
    }

    private suspend fun commitProgressUpdates() {
        progressUpdateMutex.withLock {
            val updatesToCommit = pendingUpdates.values.toList().also {
                pendingUpdates.clear()
            }

            db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                val (attemptFailedUpdates, normalUpdates) = updatesToCommit.partition { it.status == STATUS_ATTEMPT_FAILED }

                normalUpdates.forEach {
                    txDb.downloadJobItemDao.updateProgressAndStatusByUid(it.downloadJobItemUid,
                        it.bytesSoFar, it.localBytesSoFar, it.originBytesSoFar,it.totalBytes, it.status)
                }

                attemptFailedUpdates.forEach {
                    txDb.downloadJobItemDao.updateProgressAndStatusOfFailedAttempt(it.downloadJobItemUid,
                        it.bytesSoFar, it.localBytesSoFar, it.originBytesSoFar,it.totalBytes, maxAttempts)
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.launchProcessor(
        id: Int,
        channel: ReceiveChannel<DownloadBatch>
    ) = launch {
        for(item in channel) {
            Napier.d("$logPrefix - processor $id - start download of " +
                    item.itemsToDownload.joinToString { it.djiOriginUrl ?: "" }, tag = Retriever.LOGTAG)

            val progressListenerWrapper = RetrieverProgressListener { evt ->
                progressUpdateMutex.withLock {
                    pendingUpdates[evt.downloadJobItemUid] = evt
                }

                progressListener.onRetrieverProgress(evt)
            }

            try {
                val host = item.host
                if(host == null) {
                    //Download from origin url
                    val itemToDownload = item.itemsToDownload.firstOrNull()
                        ?: throw IllegalArgumentException("Batch to download from origin has no item!")

                    Napier.d("$logPrefix - processor $id - fetch from origin server " +
                            item.itemsToDownload.joinToString { it.djiOriginUrl ?: "" }, tag = Retriever.LOGTAG)
                    originServerFetcher.download(itemToDownload, progressListenerWrapper)
                    db.withDoorTransactionAsync(RetrieverDatabase::class) { txDb ->
                        txDb.downloadJobItemDao.updateStatusByUid(itemToDownload.djiUid, STATUS_COMPLETE)
                    }
                    Napier.d("$logPrefix - processor $id - fetch from origin server done" +
                            item.itemsToDownload.joinToString { it.djiOriginUrl ?: "" }, tag = Retriever.LOGTAG)
                }else {
                    Napier.d("$logPrefix - processor $id - fetch from peer $host starting", tag = Retriever.LOGTAG)

                    try {
                        localPeerFetcher.download(host, item.itemsToDownload, progressListenerWrapper)
                        Napier.d("$logPrefix - processor $id - fetch from peer $host done.",
                            tag = Retriever.LOGTAG)
                    }catch(e: Exception) {
                        throw e
                    }
                }
            }catch(e: Exception) {
                //TODO: retry logic
                throw e
            }finally {
                withContext(NonCancellable) {
                    commitProgressUpdates()
                    checkQueueChannel.send(true)
                }

            }

        }
    }

    suspend fun download() {
        Napier.i("$logPrefix download started")
        withContext(Dispatchers.Default) {
            val producer = produceJobs()
            val jobList = mutableListOf<Job>()
            try {
                coroutineScope {
                    repeat(maxConcurrent) {
                        jobList += launchProcessor(it, producer)
                    }
                    checkQueueChannel.send(true)
                }
            }catch(e: Exception) {
                throw e
            }
        }
        Napier.i("$logPrefix download finished")
    }



}