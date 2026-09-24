package com.goldmedal.aillm.chat.websearch

/**
 * A web search the app could not hand to a browser automatically, kept so the
 * UI can show the user how to finish it: tap the URL, or copy the query.
 *
 * The app never fetches or parses results — it only ever hands text to
 * whatever browser the device has.
 */
data class WebSearchFallback(
    /** The query the chat model produced. Always safe to copy by hand. */
    val query: String,
    /** A ready-made search URL, or null when it could not be built. */
    val url: String?
)
