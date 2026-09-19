package com.goldmedal.aillm.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BraveSearchProvider @Inject constructor(
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
            val request = Request.Builder()
                .url("https://api.search.brave.com/res/v1/web/search?q=$query&count=5")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Encoding", "gzip")
                .addHeader("X-Subscription-Token", apiKey)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Brave search failed: ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val jsonRes = json.parseToJsonElement(responseBody) as JsonObject
            val results = jsonRes["web"]?.jsonObject?.get("results")?.jsonArray ?: return@withContext Result.failure(Exception("No results"))

            val items = results.map { result ->
                val obj = result.jsonObject
                SearchItem(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    url = obj["url"]?.jsonPrimitive?.content ?: "",
                    snippet = obj["description"]?.jsonPrimitive?.content ?: "",
                    source = "Brave Search"
                )
            }

            Result.success(SearchResult(query = query, results = items))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
