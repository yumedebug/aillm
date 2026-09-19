package com.goldmedal.aillm.core.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    }

    fun saveApiKey(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getApiKey(key: String): String? {
        return prefs.getString(key, null)
    }

    fun removeApiKey(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun hasApiKey(key: String): Boolean {
        return prefs.contains(key)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
