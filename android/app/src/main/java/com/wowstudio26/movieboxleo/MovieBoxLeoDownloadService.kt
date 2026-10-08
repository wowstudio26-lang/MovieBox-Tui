@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wowstudio26.movieboxleo

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import java.util.List

class MovieBoxLeoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    "MovieBox Leo downloads",
    0
) {
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 4101
        private const val CHANNEL_ID = "moviebox-leo-downloads"
    }

    override fun getDownloadManager(): DownloadManager {
        return DownloadManagerHolder.manager(this)
    }

    override fun getScheduler() = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadNotificationHelper(this, CHANNEL_ID)
            .buildProgressNotification(
                this,
                android.R.drawable.stat_sys_download,
                null,
                "MovieBox Leo downloads",
                downloads,
                notMetRequirements
            )
    }
}
