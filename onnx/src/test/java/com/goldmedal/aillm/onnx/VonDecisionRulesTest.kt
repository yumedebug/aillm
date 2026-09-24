package com.goldmedal.aillm.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VonDecisionRulesTest {

    @Test
    fun `negation is a strong no`() {
        val verdict = VonDecisionRules.decide("東京都は日本のものではない")
        assertEquals(false, verdict?.yes)
        assertEquals(0.9f, verdict?.confidence ?: 0f, 0.001f)
    }

    @Test
    fun `english negation is a strong no`() {
        assertEquals(false, VonDecisionRules.decide("Tokyo is not a country")?.yes)
    }

    @Test
    fun `explicit agreement is a strong yes`() {
        assertEquals(true, VonDecisionRules.decide("はい、そうです")?.yes)
        assertEquals(true, VonDecisionRules.decide("That is correct.")?.yes)
    }

    @Test
    fun `a question with no lexical signal is left to the model`() {
        // A genuine knowledge question is exactly what the heuristics must not
        // pretend to answer.
        assertNull(VonDecisionRules.decide("東京都は日本のものか？"))
    }

    @Test
    fun `a plain statement is a weak yes`() {
        val verdict = VonDecisionRules.decide("それは良い考えです")
        assertEquals(true, verdict?.yes)
        assertTrue((verdict?.confidence ?: 1f) < 0.9f)
    }

    @Test
    fun `empty input has no verdict`() {
        assertNull(VonDecisionRules.decide("   "))
    }

    @Test
    fun `the default is an unsure no`() {
        assertFalse(VonDecisionRules.default().yes)
        assertEquals(0.5f, VonDecisionRules.default().confidence, 0.001f)
    }

    @Test
    fun `a negated pair holds with low probability`() {
        val p = VonDecisionRules.holdsProbability("東京都は日本のものではない → 日本のもの")
        assertTrue(p <= 0.49f)
    }

    @Test
    fun `an affirmed pair holds with high probability`() {
        val p = VonDecisionRules.holdsProbability("東京都です、そうです → 日本のもの")
        assertTrue(p >= 0.51f)
    }

    @Test
    fun `a pair with no lexical signal stays at not-clear`() {
        // No signal must not become a fake confident answer: 0.5 is exactly the
        // C (Not Clear) band of the verdict scale.
        assertEquals(0.5f, VonDecisionRules.holdsProbability("東京都 → 日本のもの"), 0.001f)
    }
}
