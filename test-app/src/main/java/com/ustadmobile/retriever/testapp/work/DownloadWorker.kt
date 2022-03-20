package com.ustadmobile.retriever.testapp.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ustadmobile.retriever.ProgressEvent
import com.ustadmobile.retriever.ProgressListener
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverRequest
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import java.io.File

class DownloadWorker(
    context: Context,
    params: WorkerParameters
): CoroutineWorker(context, params), DIAware, ProgressListener{

    override val di: DI by closestDI(context)

    private val retriever: Retriever by instance()

    override fun onProgress(progressEvent: ProgressEvent) {

    }

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_URL) ?: throw IllegalArgumentException("No url!")
        val downloadDir = File(applicationContext.dataDir, "downloads")
        downloadDir.takeIf { !it.exists() }?.mkdirs()
        val fileName = downloadUrl.substringAfterLast("/")
        val downloadDestFile = File(downloadDir, fileName)

        val retrieverRequests = listOf(RetrieverRequest(downloadUrl, downloadDestFile.absolutePath, 0))
        retriever.retrieve(retrieverRequests, this)
        return Result.success()
    }

    companion object {
        const val KEY_URL = "url"
    }

}