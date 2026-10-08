package com.wowstudio26.movieboxleo

import org.json.JSONObject

data class SearchResult(
    val id: String,
    val title: String,
    val year: String,
    val mediaType: String,
    val posterUrl: String?,
    val provider: String
)

data class MediaDetails(
    val id: String,
    val title: String,
    val mediaType: String,
    val year: String,
    val description: String,
    val tagline: String,
    val imdbRating: String,
    val director: String,
    val stars: String,
    val posterUrl: String?,
    val duration: String,
    val genres: List<String>
)

data class PlaybackInfo(
    val url: String,
    val headers: Map<String, String>
)

object RustBridge {
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("moviebox_tui")
        } catch (error: Throwable) {
            loadError = "Native Rust engine is not available: \${error.message ?: "unknown error"}"
        }
    }

    fun search(query: String): Result<List<SearchResult>> {
        loadError?.let { return Result.failure(IllegalStateException(it)) }

        return runCatching {
            val root = JSONObject(nativeSearch(query))
            if (!root.optBoolean("ok", false)) {
                throw IllegalStateException(root.optString("error", "Rust search failed"))
            }
            val array = root.optJSONArray("results")
            buildList {
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            SearchResult(
                                id = item.optString("id"),
                                title = item.optString("title"),
                                year = item.optString("year"),
                                mediaType = item.optString("media_type"),
                                posterUrl = item.optString("poster_url").takeIf { it.isNotBlank() },
                                provider = item.optString("provider")
                            )
                        )
                    }
                }
            }
        }
    }

    fun details(id: String): Result<MediaDetails> {
        loadError?.let { return Result.failure(IllegalStateException(it)) }

        return runCatching {
            val root = JSONObject(nativeDetails(id))
            if (!root.optBoolean("ok", false)) {
                throw IllegalStateException(
                    root.optString("error", "Unable to load details")
                )
            }

            val item = root.getJSONObject("details")
            MediaDetails(
                id = item.optJSONObject("id")?.optString("value").orEmpty(),
                title = item.optString("title"),
                mediaType = item.optString("media_type"),
                year = item.optString("year"),
                description = item.optString("description"),
                tagline = item.optString("tagline"),
                imdbRating = item.optString("imdb_rating"),
                director = item.optString("director"),
                stars = item.optString("stars"),
                posterUrl = item.optString("poster_url").takeIf { it.isNotBlank() },
                duration = item.optString("duration"),
                genres = buildList {
                    val genres = item.optJSONArray("genres")
                    if (genres != null) {
                        for (i in 0 until genres.length()) {
                            add(genres.optString(i))
                        }
                    }
                }
            )
        }
    }

    fun playback(id: String): Result<PlaybackInfo> {
        loadError?.let { return Result.failure(IllegalStateException(it)) }

        return runCatching {
            val root = JSONObject(nativePlayback(id))
            if (!root.optBoolean("ok", false)) {
                throw IllegalStateException(
                    root.optString("error", "No playable stream found")
                )
            }

            val url = root.optString("url").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No playable stream URL found")

            val headers = buildMap {
                val array = root.optJSONArray("headers")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val pair = array.getJSONArray(i)
                        if (pair.length() >= 2) {
                            put(pair.optString(0), pair.optString(1))
                        }
                    }
                }
            }

            PlaybackInfo(url, headers)
        }
    }

    private external fun nativeSearch(query: String): String
    private external fun nativeDetails(id: String): String
    private external fun nativePlayback(id: String): String
}
