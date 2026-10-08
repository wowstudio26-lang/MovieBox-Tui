@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wowstudio26.movieboxleo

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class LeoDownloadItem(
    val id: String,
    val title: String,
    val state: Int,
    val percent: Float,
    val bytesDownloaded: Long,
    val contentLength: Long,
    val url: String
)

data class LeoDownloadMetadata(
    val title: String,
    val posterUrl: String?,
    val headers: Map<String, String>
)

object DownloadManagerHolder {
    private const val CACHE_DIR = "moviebox-leo-downloads"
    private const val META_VERSION = 1

    private var initializedContext: Context? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var downloadCache: SimpleCache? = null
    private var downloadIndex: DefaultDownloadIndex? = null
    private var downloadManager: DownloadManager? = null
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)

    @Synchronized
    fun initialize(context: Context) {
        if (downloadManager != null) return

        val appContext = context.applicationContext
        initializedContext = appContext
        val database = StandaloneDatabaseProvider(appContext)
        val cacheDir = File(
            appContext.getExternalFilesDir(null) ?: appContext.filesDir,
            CACHE_DIR
        )
        val cache = SimpleCache(cacheDir, NoOpCacheEvictor(), database)
        val index = DefaultDownloadIndex(database)
        val downloaderFactory = HeaderAwareDownloaderFactory(cache, executor)

        databaseProvider = database
        downloadCache = cache
        downloadIndex = index
        downloadManager = DownloadManager(appContext, index, downloaderFactory).apply {
            maxParallelDownloads = 2
            minRetryCount = 5
            resumeDownloads()
        }
    }

    fun manager(context: Context): DownloadManager {
        initialize(context)
        return checkNotNull(downloadManager)
    }

    fun index(context: Context): DefaultDownloadIndex {
        initialize(context)
        return checkNotNull(downloadIndex)
    }

    fun cache(context: Context): SimpleCache {
        initialize(context)
        return checkNotNull(downloadCache)
    }

    fun addDownload(
        context: Context,
        id: String,
        url: String,
        title: String,
        posterUrl: String?,
        headers: Map<String, String>
    ) {
        val requestData = encodeMetadata(LeoDownloadMetadata(title, posterUrl, headers))
        val request = DownloadRequest.Builder(id, Uri.parse(url))
            .setData(requestData)
            .build()
        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
            context,
            MovieBoxLeoDownloadService::class.java,
            request,
            false
        )
    }

    fun removeDownload(context: Context, id: String) {
        androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
            context,
            MovieBoxLeoDownloadService::class.java,
            id,
            false
        )
    }

    fun pauseDownload(context: Context, id: String) {
        androidx.media3.exoplayer.offline.DownloadService.sendSetStopReason(
            context,
            MovieBoxLeoDownloadService::class.java,
            id,
            1,
            false
        )
    }

    fun resumeDownload(context: Context, id: String) {
        androidx.media3.exoplayer.offline.DownloadService.sendSetStopReason(
            context,
            MovieBoxLeoDownloadService::class.java,
            id,
            Download.STOP_REASON_NONE,
            false
        )
    }

    fun pauseDownloads(context: Context) {
        androidx.media3.exoplayer.offline.DownloadService.sendPauseDownloads(
            context,
            MovieBoxLeoDownloadService::class.java,
            false
        )
    }

    fun resumeDownloads(context: Context) {
        androidx.media3.exoplayer.offline.DownloadService.sendResumeDownloads(
            context,
            MovieBoxLeoDownloadService::class.java,
            false
        )
    }

    fun downloads(context: Context): List<LeoDownloadItem> {
        val cursor = index(context).getDownloads(intArrayOf())
        return buildList {
            try {
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    val metadata = metadata(download)
                    add(
                        LeoDownloadItem(
                            id = download.request.id,
                            title = metadata.title,
                            state = download.state,
                            percent = download.percentDownloaded,
                            bytesDownloaded = download.bytesDownloaded,
                            contentLength = download.contentLength,
                            url = download.request.uri.toString()
                        )
                    )
                }
            } finally {
                cursor.close()
            }
        }.sortedWith(compareBy({ it.state == Download.STATE_COMPLETED }, { it.title.lowercase() }))
    }

    fun metadata(download: Download): LeoDownloadMetadata {
        return decodeMetadata(download.request.data)
    }

    fun cachedPlaybackDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
    }

    private fun encodeMetadata(metadata: LeoDownloadMetadata): ByteArray {
        val root = JSONObject()
            .put("version", META_VERSION)
            .put("title", metadata.title)
            .put("poster_url", metadata.posterUrl ?: "")
        val headers = JSONArray()
        metadata.headers.forEach { (name, value) ->
            headers.put(JSONArray().put(name).put(value))
        }
        root.put("headers", headers)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decodeMetadata(data: ByteArray): LeoDownloadMetadata {
        return runCatching {
            val root = JSONObject(String(data, Charsets.UTF_8))
            val headers = buildMap {
                val array = root.optJSONArray("headers")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val pair = array.optJSONArray(i) ?: continue
                        if (pair.length() >= 2) {
                            put(pair.optString(0), pair.optString(1))
                        }
                    }
                }
            }
            LeoDownloadMetadata(
                title = root.optString("title").ifBlank { "MovieBox Leo Download" },
                posterUrl = root.optString("poster_url").takeIf { it.isNotBlank() },
                headers = headers
            )
        }.getOrDefault(
            LeoDownloadMetadata(
                title = "MovieBox Leo Download",
                posterUrl = null,
                headers = emptyMap()
            )
        )
    }

    private class HeaderAwareDownloaderFactory(
        private val cache: SimpleCache,
        private val executor: ExecutorService
    ) : DownloaderFactory {
        override fun createDownloader(request: DownloadRequest): Downloader {
            val metadata = decodeRequestMetadata(request.data)
            val httpFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(metadata.headers)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
            return DefaultDownloaderFactory(cacheDataSourceFactory, executor)
                .createDownloader(request)
        }

        private fun decodeRequestMetadata(data: ByteArray): LeoDownloadMetadata {
            return runCatching {
                val root = JSONObject(String(data, Charsets.UTF_8))
                val headers = buildMap {
                    val array = root.optJSONArray("headers")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val pair = array.optJSONArray(i) ?: continue
                            if (pair.length() >= 2) {
                                put(pair.optString(0), pair.optString(1))
                            }
                        }
                    }
                }
                LeoDownloadMetadata(
                    title = root.optString("title").ifBlank { "MovieBox Leo Download" },
                    posterUrl = root.optString("poster_url").takeIf { it.isNotBlank() },
                    headers = headers
                )
            }.getOrDefault(
                LeoDownloadMetadata(
                    title = "MovieBox Leo Download",
                    posterUrl = null,
                    headers = emptyMap()
                )
            )
        }
    }
}
