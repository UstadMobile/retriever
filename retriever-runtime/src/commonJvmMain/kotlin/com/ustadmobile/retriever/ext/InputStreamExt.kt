package com.ustadmobile.retriever.ext

import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.Retriever.Companion.STATUS_RUNNING
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverListener

//The read/write has to actually happen somewhere. This function regularly checks
//for cancellation and behaves appropriately.
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun InputStream.copyToAndUpdateProgress(
    dest: OutputStream,
    progressListener: RetrieverListener,
    downloadJobItemUid: Int,
    url: String,
    totalBytes: Long,
    progressInterval: Int = 333,
) : Long {
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
                RetrieverProgressEvent(downloadJobItemUid, url, totalBytesRead, 0L, totalBytesRead,
                    totalBytes))
            lastProgressTime = timeNow
        }
    }

    dest.flush()

    return totalBytesRead
}

//The read/write has to actually happen somewhere. This function regularly checks
//for cancellation and behaves appropriately.
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun InputStream.copyToAsync(
    dest: OutputStream,
    bufferSize: Int = 8192
) {
    val buffer = ByteArray(bufferSize)
    var bytesRead = 0

    while(coroutineContext.isActive && this.read(buffer).also { bytesRead = it} != -1) {
        dest.write(buffer, 0, bytesRead)
    }

    dest.flush()
}

