package com.ustadmobile.retriever.ext

import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.lib.db.entities.DownloadJobItem.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverProgressListener

//The read/write has to actually happen somewhere. This function regularly checks
//for cancellation and behaves appropriately.
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun InputStream.copyToAndUpdateProgress(
    dest: OutputStream,
    progressListener: RetrieverProgressListener,
    downloadJobItemUid: Long,
    url: String,
    totalBytes: Long,
    progressInterval: Int = 333,
) {
    val buf = ByteArray(8 * 1024)
    var bytesRead = 0
    var totalBytesRead = 0L
    var lastProgressTime = systemTimeInMillis()

    while(coroutineContext.isActive && this.read(buf).also { bytesRead = it} != -1) {
        dest.write(buf, 0, bytesRead)
        totalBytesRead += bytesRead
        val timeNow = systemTimeInMillis()
        if(timeNow - lastProgressTime >= progressInterval){
            progressListener.onRetrieverProgress(
                RetrieverProgressEvent(downloadJobItemUid, url, totalBytesRead, totalBytes, STATUS_RUNNING))
            lastProgressTime = timeNow
        }
    }

    progressListener.onRetrieverProgress(RetrieverProgressEvent(downloadJobItemUid, url, totalBytesRead, totalBytes,
        STATUS_RUNNING))
    dest.flush()
}
