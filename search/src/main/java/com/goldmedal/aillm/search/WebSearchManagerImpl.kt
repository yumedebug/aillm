package com.goldmedal.aillm.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "web_search")

@Singleton
class WebSearchManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val braveSearchProvider: BraveSearchProvider,
    private val tavilySearchProvider: TavilySearchProvider
) : WebSearchManager {

    companion object {
        private val SEARCH_ENABLED = booleanPreferencesKey("search_enabled")
        private val BRAVE_API_KEY = stringPreferencesKey("brave_api_key")
        private val TAVILY_API_KEY = stringPreferencesKey("tavily_api_key")
    }

    override suspend fun search(query: String): Result<SearchResult> {
        if (!isEnabled()) {
            return Result.failure(Exception("Web search is disabled"))
        }

        val braveApiKey = getBraveApiKey()
        val tavilyApiKey = getTavilyApiKey()

        // Try Brave first
        if (braveApiKey.isNotBlank()) {
            val braveResult = braveSearchProvider.search(query, braveApiKey)
            if (braveResult.isSuccess) {
                return braveResult
            }
        }

        // Fallback to Tavily
        if (tavilyApiKey.isNotBlank()) {
            val tavilyResult = tavilySearchProvider.search(query, tavilyApiKey)
            if (tavilyResult.isSuccess) {
                return tavilyResult
            }
        }

        return Result.failure(Exception("No search provider available or all failed"))
    }

    override fun isEnabled(): Boolean {
        return runBlocking {
            context.searchDataStore.data.map { it[SEARCH_ENABLED] ?: false }.first()
        }
    }

    fun isEnabledFlow(): Flow<Boolean> {
        return context.searchDataStore.data.map { it[SEARCH_ENABLED] ?: false }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.searchDataStore.edit { it[SEARCH_ENABLED] = enabled }
    }

    override suspend fun getApiKey(): String? {
        return getBraveApiKey().ifBlank { null }
    }

    override suspend fun setApiKey(key: String) {
        setBraveApiKey(key)
    }

    suspend fun setBraveApiKey(key: String) {
        context.searchDataStore.edit { it[BRAVE_API_KEY] = key }
    }

    suspend fun setTavilyApiKey(key: String) {
        context.searchDataStore.edit { it[TAVILY_API_KEY] = key }
    }

    suspend fun getBraveApiKey(): String {
        return context.searchDataStore.data.map { it[BRAVE_API_KEY] ?: "" }.first()
    }

    suspend fun getTavilyApiKey(): String {
        return context.searchDataStore.data.map { it[TAVILY_API_KEY] ?: "" }.first()
    }
}
