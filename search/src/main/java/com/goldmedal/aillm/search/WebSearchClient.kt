package com.goldmedal.aillm.search

import javax.inject.Inject
import javax.inject.Singleton

data class SearchResult(
    val query: String,
    val results: List<SearchItem>,
    val timestamp: Long = System.currentTimeMillis()
)

data class SearchItem(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * The app talks to online sources only through this interface.
 *
 * The concrete implementation is expected to call a developer-controlled
 * search backend (which in turn holds the provider keys). Secrets must never
 * live in the APK, and end users must never be asked for an API key.
 *
 * Online sources are OFF by default; while disabled, and while no backend is
 * configured, the chat engine is given no search tool at all.
 */
interface WebSearchClient {
    suspend fun search(query: String): Result<SearchResult>
}

@Singleton
class NoopWebSearchClient @Inject constructor() : WebSearchClient {
    override suspend fun search(query: String): Result<SearchResult> =
        Result.failure(IllegalStateException("Online sources are not configured."))
}
