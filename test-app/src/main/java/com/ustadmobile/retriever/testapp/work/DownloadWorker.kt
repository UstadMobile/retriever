package com.ustadmobile.retriever.testapp.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverRequest
import com.ustadmobile.retriever.RetrieverStatusUpdateEvent
import com.ustadmobile.retriever.fetcher.RetrieverProgressEvent
import com.ustadmobile.retriever.fetcher.RetrieverListener
import com.ustadmobile.retriever.testapp.R
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.instance
import java.io.File

class DownloadWorker(
    context: Context,
    params: WorkerParameters
): CoroutineWorker(context, params), DIAware, RetrieverListener{

    override val di: DI by closestDI(context)

    private val retriever: Retriever by instance()

    private lateinit var notificationBuilder: NotificationCompat.Builder

    private var notificationId = 0L

    private val notificationManager = ContextCompat.getSystemService(
        applicationContext,
        NotificationManager::class.java
    ) as NotificationManager

    private fun createNotificationBuilder(downloadUrl: String) :NotificationCompat.Builder{
        notificationId = System.currentTimeMillis()

        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        return NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(downloadUrl)
            .setOngoing(true)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_file_download_white_24dp)
            .addAction(android.R.drawable.ic_delete, applicationContext.getString(R.string.cancel), intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val mNotificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)

        mNotificationChannel.vibrationPattern = longArrayOf(0)
        mNotificationChannel.enableVibration(false)
        mNotificationChannel.setSound(null, null)

        notificationManager.createNotificationChannel(mNotificationChannel)
    }


    override fun isRunInForeground(): Boolean {
        return true
    }

    override suspend fun onRetrieverProgress(retrieverProgressEvent: RetrieverProgressEvent) {
        notificationBuilder.setProgress(retrieverProgressEvent.totalBytes.toInt(),
            retrieverProgressEvent.bytesSoFar.toInt(), retrieverProgressEvent.totalBytes == 0L)

        setForegroundAsync(ForegroundInfo(notificationId.hashCode(), notificationBuilder.build()))
    }

    override suspend fun onRetrieverStatusUpdate(retrieverStatusEvent: RetrieverStatusUpdateEvent) {

    }

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_URL) ?: throw IllegalArgumentException("No url!")
        notificationBuilder = createNotificationBuilder(downloadUrl)
        val downloadDir = File(applicationContext.filesDir, "downloads")
        downloadDir.takeIf { !it.exists() }?.mkdirs()
        val fileName = downloadUrl.substringAfterLast("/")
        val downloadDestFile = File(downloadDir, fileName)

        val retrieverRequests = listOf(RetrieverRequest(downloadUrl, downloadDestFile.absolutePath, null))
        retriever.retrieve(retrieverRequests, this)
        return Result.success()
    }

    companion object {
        const val KEY_URL = "url"

        const val NOTIFICATION_CHANNEL_ID = "RETRIEVER_NOTIFICATION_CHANNEL_ID"
    }

}