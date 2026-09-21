package com.goldmedal.aillm.search

import com.goldmedal.aillm.core.preferences.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Online sources" — the deliberately low-key name for web search.
 *
 * Disabled by default. Only when the user turns it on do we consider attaching
 * a search tool to the model; even then, at most one logical search happens per
 * user message.
 */
@Singleton
class OnlineSources @Inject constructor(
    private val appSettings: AppSettings
) {
    val enabled: Flow<Boolean> = appSettings.onlineSourcesEnabled

    suspend fun isEnabled(): Boolean = appSettings.onlineSourcesEnabled.first()

    suspend fun setEnabled(value: Boolean) = appSettings.setOnlineSourcesEnabled(value)

    /** True only when the feature is on *and* a backend is actually reachable. */
    fun isAvailable(): Boolean = false
}
