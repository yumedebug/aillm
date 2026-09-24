package com.goldmedal.aillm.search

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What happened when the app handed a search to the device. The app never
 * fetches or parses results; the browser is where the user reads them.
 */
sealed interface BrowserSearchOutcome {
    /** A browser or search surface opened for [query]. */
    data class Opened(val query: String) : BrowserSearchOutcome

    /** Nothing would start; [url] is shown so the user can tap it. */
    data class ShowUrl(val query: String, val url: String) : BrowserSearchOutcome

    /** Not even the URL could be built; the raw [query] is shown to copy. */
    data class ShowQuery(val query: String) : BrowserSearchOutcome
}

/**
 * Hands a web search to the device's browser — no search API, no key, no
 * backend. The fallback chain degrades step by step and never throws:
 *
 * 1. `ACTION_WEB_SEARCH` — the system's default way to search,
 * 2. `ACTION_VIEW` on a concrete search URL — resolved by the default browser,
 * 3. show the search URL so the user can tap it themselves,
 * 4. show the raw query so the user can copy it.
 */
@Singleton
class BrowserSearchLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launch(query: String): BrowserSearchOutcome {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return BrowserSearchOutcome.ShowQuery(query)

        val webSearch = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, trimmed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(webSearch)) return BrowserSearchOutcome.Opened(trimmed)

        val url = searchUrl(trimmed)
        if (url != null && startSafely(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        ) {
            return BrowserSearchOutcome.Opened(trimmed)
        }

        if (url != null) return BrowserSearchOutcome.ShowUrl(trimmed, url)
        return BrowserSearchOutcome.ShowQuery(trimmed)
    }

    /**
     * Opens an already-built search URL in the default browser. Used when the
     * user taps the link the app showed after the automatic launch failed.
     * Returns false when nothing on the device would handle it.
     */
    fun openUrl(url: String): Boolean = startSafely(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )

    /** The URL used by the URL-fallback step. Null only if encoding fails. */
    fun searchUrl(query: String): String? = runCatching {
        SEARCH_URL_BASE + URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    }.getOrNull()

    private fun startSafely(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        // SecurityException and friends must never take the app down.
        false
    }

    companion object {
        private const val SEARCH_URL_BASE = "https://www.google.com/search?q="
    }
}
