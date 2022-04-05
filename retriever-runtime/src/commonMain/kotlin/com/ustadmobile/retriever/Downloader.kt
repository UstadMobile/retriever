package com.ustadmobile.retriever

import com.ustadmobile.door.ext.concurrentSafeListOf
import com.ustadmobile.door.ext.concurrentSafeMapOf
import com.ustadmobile.door.ext.withDoorTransactionAsync
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem
import com.ustadmobile.lib.db.entities.DownloadJobItemAndNodeInfo
import com.ustadmobile.retriever.Retriever.Companion.STATUS_ATTEMPT_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_FAILED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_QUEUED
import com.ustadmobile.retriever.Retriever.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.db.RetrieverDatabase
import com.ustadmobile.retriever.fetcher.LocalPeerFetcher
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverListener
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
 *
 * @param maxPeerNodeFailuresAllowed the maximum number of failures to allow for a peer node. If this is exceeded, we
 *        won't download from this peer
 * @param peerNodeFailureTimeThreshold the duration during which failures will be counted.
 */
class Downloader(
    private val downloadBatchId: Long,
    private val availabilityManager: AvailabilityManager,
    private val progressListener: RetrieverListener,
    private val originServerFetcher: OriginServerFetcher,
    private val localPeerFetcher: LocalPeerFetcher,
    private val db: RetrieverDatabase,
    private val maxConcurrent: Int = 8,
    private val maxAttempts: Int = 8,
    private val attemptRetryDelay: Int = 1000,
    private val maxPeerNodeFailuresAllowed: Int = 3,
    private val peerNodeFailureTimeThreshold: Long = (1000 * 60 * 3),
    private val availabilityWaitForPeersTimeout: Long = 1000, //by default wait for up to one second to find local peer info
) {

    private val checkQueueChannel = Channel<Boolean>(capacity = Channel.UNLIMITED)

    private data class DownloadBatch(val host: String?, val itemsToDownload: List<DownloadJobItem>)

    private val activeBatches: MutableList<DownloadBatch> = concurrentSafeListOf()

    private val logPrefix = "[Retriever-Downloader #$downloadBatchId] "

    private val downloadJobItemUpdateMutex = Mutex()

    private val pendingUpdates = concurrentSafeMapOf<Int, RetrieverProgressEvent>()

    private suspend fun <R> updateDownloadJobItemTransaction(block: suspend (RetrieverDatabase) -> R): R {
        return downloadJobItemUpdateMutex.withLock {
            db.withDoorTransactionAsync(RetrieverDatabase::class) {txDb ->
                block(txDb)
            }
        }
    }

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
                val batchesToSend = updateDownloadJobItemTransaction { txDb ->
                    val queueItems = txDb.downloadJobItemDao.findNextItemsToDownload(downloadBatchId,
                        maxPeerNodeFailuresAllowed, systemTimeInMillis() - peerNodeFailureTimeThreshold)
                    //turn these into batches
                    val groupedByNode = queueItems.groupBy { it.networkNodeId }.toMutableMap()
                    val locallyAvailableBatches = groupedByNode.entries.filter { it.key != 0 }
                        .map {  entry ->
                            DownloadBatch(entry.value.first().networkNodeEndpointUrl, entry.value.sortedBy { it.djiIndex })
                        }
                    val locallyAvailableBatchesToSend = locallyAvailableBatches.subList(0,
                        min(numProcessorsAvailable, locallyAvailableBatches.size))

                    //For items that cannot be downloaded locally, split them into equally sized batches according to
                    // the number of processors available.
                    val numFromOriginBatches = min(groupedByNode[0]?.size ?: 0,
                        numProcessorsAvailable - locallyAvailableBatchesToSend.size)

                    val originBatches = (0 until numFromOriginBatches).map { mutableListOf<DownloadJobItemAndNodeInfo>() }
                        .toMutableList()

                    groupedByNode[0]?.forEachIndexed { index, downloadJobItemAndNodeInfo ->
                        originBatches[index % numFromOriginBatches].add(downloadJobItemAndNodeInfo)
                    }

                    val originDownloadBatchesToSend = originBatches.map {
                        DownloadBatch(null, it.toList())
                    }

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

    private suspend fun RetrieverDatabase.commitProgressUpdates() {
        val updatesToCommit = pendingUpdates.values.toList().also {
            pendingUpdates.clear()
        }

        updatesToCommit.forEach {
            downloadJobItemDao.updateProgressByUid(it.downloadJobItemUid,
                it.bytesSoFar, it.localBytesSoFar, it.originBytesSoFar, it.totalBytes)
        }
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.launchProcessor(
        id: Int,
        channel: ReceiveChannel<DownloadBatch>
    ) = launch {
        class StatusCommit(val status: Int, val attempts: Int)

        fun DownloadJobItem.statusIfAttemptFailed() : Int{
            return if ((djiAttemptCount + 1) >= maxAttempts) { STATUS_FAILED } else { STATUS_QUEUED }
        }

        for(item in channel) {
            Napier.d("$logPrefix - processor $id - start download of " +
                    item.itemsToDownload.joinToString { it.djiOriginUrl ?: "" }, tag = Retriever.LOGTAG)

            var hasFailedAttempts = false
            val host = item.host

            val jobItemIdsStarted = mutableSetOf<Int>()

            //The final statuses of items that we will commit to the database when this run is finished
            val statusCommits = concurrentSafeMapOf<Int, StatusCommit>()
            val progressListenerWrapper = object: RetrieverListener {
                override suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent) {
                    jobItemIdsStarted += retrieverProgressEvent.downloadJobItemUid

                    downloadJobItemUpdateMutex.withLock {
                        Napier.d("DownloadJobItem update: bytes so far: ${retrieverProgressEvent.bytesSoFar}")
                        pendingUpdates[retrieverProgressEvent.downloadJobItemUid] = retrieverProgressEvent
                    }

                    progressListener.onRetrieverProgress(retrieverProgressEvent)
                }

                override suspend fun onRetrieverStatusUpdate(retrieverStatusEvent: RetrieverStatusUpdateEvent) {
                    hasFailedAttempts = hasFailedAttempts || retrieverStatusEvent.status == STATUS_ATTEMPT_FAILED

                    val isFailedAttempt = (retrieverStatusEvent.status == STATUS_ATTEMPT_FAILED)
                    val downloadItem = item.itemsToDownload.first {it.djiUid == retrieverStatusEvent.downloadJobItemUid }
                    var attemptCount = downloadItem.djiAttemptCount
                    val newEvt =  when {
                        isFailedAttempt -> {
                            attemptCount++
                            retrieverStatusEvent.copy(status = downloadItem.statusIfAttemptFailed())
                        }
                        else -> retrieverStatusEvent
                    }

                    statusCommits[retrieverStatusEvent.downloadJobItemUid] = StatusCommit(newEvt.status,
                        attemptCount)

                    progressListener.onRetrieverStatusUpdate(newEvt)
                }
            }

            try {
                if(host == null) {
                    //Download from origin url
                    Napier.d("$logPrefix - processor $id - fetch from origin server " +
                            item.itemsToDownload.joinToString { it.djiOriginUrl ?: "" }, tag = Retriever.LOGTAG)
                    originServerFetcher.download(item.itemsToDownload, progressListenerWrapper)
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
                hasFailedAttempts = true
                Napier.e("Exception running downloader", e)
            }finally {
                withContext(NonCancellable) {
                    if(hasFailedAttempts) {
                        if(host != null) {
                            updateDownloadJobItemTransaction { txDb ->
                                txDb.networkNodeFailureDao.insertFailureUsingEndpoint(host, systemTimeInMillis())
                            }
                        }

                        delay(attemptRetryDelay.toLong())
                    }


                    updateDownloadJobItemTransaction { txDb ->
                        statusCommits.forEach {
                            Napier.d("$logPrefix - processor $id Update Job status of # ${it.key} = ${it.value.status} (${systemTimeInMillis()})")
                            txDb.downloadJobItemDao.updateStatusAndAttemptCountByUid(it.key, it.value.status,
                                it.value.attempts)
                        }

                        /*
                         * Normally the Fetcher (e.g. local or peer) should send a final status change event for each item.
                         * In case that doesn't happen (eg interrupted, exception, etc), we need to make sure that we
                         * set a valid final status for each downloadjobitem in the list that was supposed to be downloaded
                         * so it will be either marked for retry (with attemptcount incremented) or marked as having
                         * permanently failed as appropriate.
                         */
                        val itemIdsWithFinalStatus = statusCommits.keys

                        val itemsWithoutFinalStatus = item.itemsToDownload.filter { it.djiUid !in itemIdsWithFinalStatus }

                        itemsWithoutFinalStatus.forEach {
                            val itemStarted = it.djiUid in jobItemIdsStarted
                            val statusToSet: Int
                            val attemptCountToSet: Int
                            if(itemStarted) {
                                statusToSet = it.statusIfAttemptFailed()
                                attemptCountToSet = it.djiAttemptCount + 1
                            }else {
                                statusToSet = STATUS_QUEUED
                                attemptCountToSet = it.djiAttemptCount
                            }

                            Napier.d("$logPrefix - processor $id Update Job status of # ${it.djiUid} = ${statusToSet} (${systemTimeInMillis()})")
                            txDb.downloadJobItemDao.updateStatusAndAttemptCountByUid(it.djiUid, statusToSet,
                                attemptCountToSet)
                        }


                        txDb.commitProgressUpdates()
                    }

                    Napier.d("$logPrefix - processor $id Requesting queue check ${systemTimeInMillis()}")
                    checkQueueChannel.send(true)
                }

            }

        }
    }

    suspend fun download() {
        Napier.i("$logPrefix download started")
        val downloadUrlList: List<String> = db.downloadJobItemDao.findAllUrlsByBatchId(downloadBatchId).filterNotNull()
        val noChecksPendingCompleteable = CompletableDeferred<Boolean>()
        val availabilityObserver = AvailabilityObserver(downloadUrlList, {
            if(!it.checksPending)
                noChecksPendingCompleteable.complete(true)
        })

        withContext(Dispatchers.Default) {
            availabilityManager.addAvailabilityObserver(availabilityObserver)

            withTimeoutOrNull(availabilityWaitForPeersTimeout) {
                noChecksPendingCompleteable.await()
            }

            val producer = produceJobs()
            val jobList = mutableListOf<Job>()
            try {
                val progressUpdateJob = async {
                    while(coroutineContext.isActive) {
                        if(pendingUpdates.isNotEmpty()) {
                            updateDownloadJobItemTransaction { txDb ->
                                txDb.commitProgressUpdates()
                            }
                        }

                        delay(500)
                    }
                }

                coroutineScope {
                    repeat(maxConcurrent) {
                        jobList += launchProcessor(it, producer)
                    }
                    checkQueueChannel.send(true)
                }

                progressUpdateJob.cancel()
            }catch(e: Exception) {
                throw e
            }finally {
                availabilityManager.removeAvailabilityObserver(availabilityObserver)
            }
        }
        Napier.i("$logPrefix download finished")
    }



}