package com.goldmedal.aillm.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TavilyRequest(
    val query: String,
    val search_depth: String = "basic",
    val max_results: Int = 5
)

@Serializable
data class TavilyResponse(
    val results: List<TavilyResult> = emptyList()
)

@Serializable
data class TavilyResult(
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val score: Double = 0.0
)

@Singleton
class TavilySearchProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        query: String,
        apiKey: String
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val requestBody = json.encodeToString(
                TavilyRequest.serializer(),
                TavilyRequest(query = query)
            )

            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Tavily search failed: ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val tavilyResponse = json.decodeFromString(TavilyResponse.serializer(), responseBody)

            val items = tavilyResponse.results.map { result ->
                SearchItem(
                    title = result.title,
                    url = result.url,
                    snippet = result.content,
                    source = "Tavily Search"
                )
            }

            Result.success(SearchResult(query = query, results = items))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
