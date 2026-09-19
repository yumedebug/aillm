package com.goldmedal.aillm.search

interface WebSearchManager {
    suspend fun search(query: String): Result<SearchResult>
    fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean)
    suspend fun getApiKey(): String?
    suspend fun setApiKey(key: String)
    suspend fun getBraveApiKey(): String
    suspend fun setBraveApiKey(key: String)
    suspend fun getTavilyApiKey(): String
    suspend fun setTavilyApiKey(key: String)
}

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
