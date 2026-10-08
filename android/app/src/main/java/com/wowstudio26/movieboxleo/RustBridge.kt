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

data class EpisodeInfo(
    val season: Int,
    val number: Int,
    val title: String
)

data class SeasonInfo(
    val number: Int,
    val episodes: List<EpisodeInfo>
)

data class AudioTrackInfo(val subjectId: String, val language: String, val label: String)

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
    val genres: List<String>,
    val seasons: List<SeasonInfo>,
    val dubs: List<AudioTrackInfo>
)

data class PlaybackOption(
    val quality: String,
    val resolution: Int,
    val url: String,
    val headers: Map<String, String>
)

data class PlaybackInfo(val options: List<PlaybackOption>) {
    val defaultOption: PlaybackOption get() = options.first()
}

object RustBridge {
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("moviebox_tui")
        } catch (error: Throwable) {
            loadError = "Native Rust engine is not available: ${error.message ?: "unknown error"}"
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
                },
                seasons = buildList {
                    val seasons = item.optJSONArray("seasons")
                    if (seasons != null) {
                        for (s in 0 until seasons.length()) {
                            val seasonObject = seasons.optJSONObject(s) ?: continue
                            val seasonNumber = seasonObject.optInt("number", 0)
                            val episodes = buildList {
                                val episodeArray = seasonObject.optJSONArray("episodes")
                                if (episodeArray != null) {
                                    for (e in 0 until episodeArray.length()) {
                                        val episodeObject = episodeArray.optJSONObject(e) ?: continue
                                        add(
                                            EpisodeInfo(
                                                season = episodeObject.optInt("season", seasonNumber),
                                                number = episodeObject.optInt("number", e + 1),
                                                title = episodeObject.optString("title")
                                            )
                                        )
                                    }
                                }
                            }
                            add(SeasonInfo(seasonNumber, episodes))
                        }
                    }
                },
                dubs = buildList {
                    val dubs = item.optJSONArray("dubs")
                    if (dubs != null) {
                        for (i in 0 until dubs.length()) {
                            val dub = dubs.optJSONObject(i) ?: continue
                            val subjectId = dub.optString("subject_id")
                            if (subjectId.isBlank()) continue
                            val language = dub.optString("language")
                            val label = dub.optString("label").ifBlank { language.ifBlank { "Original" } }
                            add(AudioTrackInfo(subjectId, language, label))
                        }
                    }
                }
            )
        }
    }

    fun playback(id: String, season: Int = 0, episode: Int = 0): Result<PlaybackInfo> {
        loadError?.let { return Result.failure(IllegalStateException(it)) }
        return runCatching {
            val root = JSONObject(nativePlayback(id, season, episode))
            if (!root.optBoolean("ok", false)) throw IllegalStateException(root.optString("error", "No playable stream found"))
            val options = buildList {
                val array = root.optJSONArray("options")
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val headers = buildMap {
                        val ha = item.optJSONArray("headers")
                        if (ha != null) for (h in 0 until ha.length()) {
                            val pair = ha.optJSONArray(h) ?: continue
                            if (pair.length() >= 2) put(pair.optString(0), pair.optString(1))
                        }
                    }
                    add(PlaybackOption(item.optString("quality").ifBlank { "Auto" }, item.optInt("resolution", 0), url, headers))
                }
            }
            if (options.isEmpty()) throw IllegalStateException("No playable stream options found")
            PlaybackInfo(options)
        }
    }

    private external fun nativeSearch(query: String): String
    private external fun nativeDetails(id: String): String
    private external fun nativePlayback(id: String, season: Int, episode: Int): String
}
