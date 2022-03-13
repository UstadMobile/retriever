package com.ustadmobile.retriever.ext

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import com.ustadmobile.door.util.systemTimeInMillis
import com.ustadmobile.retriever.fetcher.FetchProgressEvent
import com.ustadmobile.retriever.fetcher.FetchProgressListener

//The read/write has to actually happen somewhere. This function regularly checks
//for cancellation and behaves appropriately.
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun InputStream.copyToAndUpdateProgress(
    dest: OutputStream,
    progressListener: FetchProgressListener,
    downloadJobItemUid: Long,
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
            progressListener.onFetchProgress(FetchProgressEvent(downloadJobItemUid, totalBytesRead, totalBytes))
            lastProgressTime = timeNow
        }
    }

    progressListener.onFetchProgress(FetchProgressEvent(downloadJobItemUid, totalBytesRead, totalBytes))
    dest.flush()
}
