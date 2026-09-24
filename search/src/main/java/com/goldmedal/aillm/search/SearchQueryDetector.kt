package com.goldmedal.aillm.search

/**
 * Decides whether a user message is asking for a web lookup, and reduces such
 * a message to a query hint.
 *
 * Purely lexical and dependency-free so it can run before any model is loaded
 * and be unit tested without Android. The real search query is still produced
 * by the chat LLM; this only gates the feature and provides the fallback hint.
 */
object SearchQueryDetector {

    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val TRIGGERS = listOf(
        // Japanese
        Regex("調べて"), Regex("調べたい"), Regex("検索して"), Regex("検索し"),
        Regex("最新情報"), Regex("最新の"), Regex("ニュース"), Regex("今日の"), Regex("現在の"),
        Regex("天気"), Regex("為替"), Regex("株価"), Regex("乗換"), Regex("営業時間"),
        Regex("いくら"), Regex("いつから"), Regex("いつまで"), Regex("どこの"),
        // English
        rx("\\bsearch\\b"), rx("look\\s+up"), rx("\\bnews\\b"),
        rx("latest\\s+(news|info|information|version|price)"),
        rx("current\\s+(news|price|version|status)"),
        rx("what(?:'s| is) the (latest|current)"), rx("who\\s+won")
    )

    private val STRIP_PHRASES = listOf(
        "をお願いします", "お願いします", "を検索して", "を調べて", "検索して", "調べて",
        "教えて", "について教えて", "の最新情報", "最新情報", "について", "を教えて",
        "please", "for me"
    )

    /** True when the message asks for something only the live web can answer. */
    fun needsSearch(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 3) return false
        return TRIGGERS.any { it.containsMatchIn(trimmed) }
    }

    /**
     * The message with search phrasing stripped — a rough query hint for when
     * the chat LLM cannot be asked to condense the request itself.
     */
    fun toQueryHint(text: String): String {
        var result = text.trim()
        STRIP_PHRASES.forEach { phrase -> result = result.replace(phrase, " ", ignoreCase = true) }
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
