@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wowstudio26.movieboxleo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService

class MovieBoxLeoDownloadService : DownloadService(FOREGROUND_NOTIFICATION_ID) {
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 4101
        private const val CHANNEL_ID = "moviebox-leo-downloads"
    }

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "MovieBox Leo downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager {
        return DownloadManagerHolder.manager(this)
    }

    override fun getScheduler() = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val active = downloads.filter {
            it.state == Download.STATE_DOWNLOADING ||
                it.state == Download.STATE_QUEUED ||
                it.state == Download.STATE_RESTARTING
        }
        val known = active.map { it.percentDownloaded }.filter { it >= 0f }
        val progress = if (known.isNotEmpty()) known.average().toInt().coerceIn(0, 100) else 0

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MovieBox Leo")
            .setContentText(
                when {
                    active.isEmpty() -> "Downloads"
                    active.size == 1 -> "Downloading " + active.first().request.id
                    else -> "Downloading " + active.size + " items"
                }
            )
            .setProgress(100, progress, known.isEmpty())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
    }
}
