package com.goldmedal.aillm.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryDetectorTest {

    @Test
    fun `a japanese request for current information triggers a search`() {
        assertTrue(SearchQueryDetector.needsSearch("2026年のPixel 8aの最新情報を調べて"))
        assertTrue(SearchQueryDetector.needsSearch("今日の天気を教えて"))
        assertTrue(SearchQueryDetector.needsSearch("東京駅の乗換を検索して"))
    }

    @Test
    fun `an english request for current information triggers a search`() {
        assertTrue(SearchQueryDetector.needsSearch("Please search for the latest Pixel news"))
        assertTrue(SearchQueryDetector.needsSearch("Who won the match last night?"))
    }

    @Test
    fun `ordinary conversation does not trigger a search`() {
        assertFalse(SearchQueryDetector.needsSearch("こんにちは、元気ですか"))
        assertFalse(SearchQueryDetector.needsSearch("Write a haiku about rain"))
    }

    @Test
    fun `very short input never triggers a search`() {
        assertFalse(SearchQueryDetector.needsSearch("ね"))
        assertFalse(SearchQueryDetector.needsSearch(""))
    }

    @Test
    fun `the hint drops the search phrasing`() {
        assertEquals(
            "2026年のPixel 8a",
            SearchQueryDetector.toQueryHint("2026年のPixel 8aの最新情報を調べて")
        )
    }

    @Test
    fun `the hint keeps a request that has nothing to strip`() {
        assertEquals("quantum computing", SearchQueryDetector.toQueryHint("quantum computing"))
    }
}
