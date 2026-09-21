package com.goldmedal.aillm.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.goldmedal.aillm.core.design.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Single source of truth for user preferences. Kept in :core so both the app
 * shell (theme) and feature modules (generation params, online sources) can
 * read from the same store without module-to-module coupling.
 *
 * Online information is OFF until the user explicitly enables it.
 */
@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val ONLINE_SOURCES = booleanPreferencesKey("online_sources_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }

    val themeMode: Flow<ThemeMode> = context.appSettingsStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "DARK" -> ThemeMode.DARK
            "LIGHT" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    val isOnboarded: Flow<Boolean> = context.appSettingsStore.data.map { it[Keys.ONBOARDED] ?: false }

    val temperature: Flow<Float> = context.appSettingsStore.data.map { it[Keys.TEMPERATURE] ?: 0.7f }

    val maxTokens: Flow<Int> = context.appSettingsStore.data.map { it[Keys.MAX_TOKENS] ?: 2048 }

    val contextLength: Flow<Int> = context.appSettingsStore.data.map { it[Keys.CONTEXT_LENGTH] ?: 4096 }

    /** Default OFF. The app must never reach out until the user opts in. */
    val onlineSourcesEnabled: Flow<Boolean> =
        context.appSettingsStore.data.map { it[Keys.ONLINE_SOURCES] ?: false }

    val reduceMotion: Flow<Boolean> = context.appSettingsStore.data.map { it[Keys.REDUCE_MOTION] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.appSettingsStore.edit { it[Keys.ONBOARDED] = value }
    }

    suspend fun setTemperature(value: Float) {
        context.appSettingsStore.edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 2f) }
    }

    suspend fun setMaxTokens(value: Int) {
        context.appSettingsStore.edit { it[Keys.MAX_TOKENS] = value.coerceIn(128, 8192) }
    }

    suspend fun setContextLength(value: Int) {
        context.appSettingsStore.edit { it[Keys.CONTEXT_LENGTH] = value.coerceIn(1024, 32768) }
    }

    suspend fun setOnlineSourcesEnabled(value: Boolean) {
        context.appSettingsStore.edit { it[Keys.ONLINE_SOURCES] = value }
    }

    suspend fun setReduceMotion(value: Boolean) {
        context.appSettingsStore.edit { it[Keys.REDUCE_MOTION] = value }
    }
}
