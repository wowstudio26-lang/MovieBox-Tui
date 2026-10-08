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
            val json = nativeSearch(query)
            val root = JSONObject(json)

            if (!root.optBoolean("ok", false)) {
                throw IllegalStateException(
                    root.optString("error", "Rust search failed")
                )
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

    private external fun nativeSearch(query: String): String
}
