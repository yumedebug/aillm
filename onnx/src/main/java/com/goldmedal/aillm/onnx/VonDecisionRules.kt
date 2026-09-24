package com.goldmedal.aillm.onnx

/**
 * Lexical fallback verdicts for the Decision model.
 *
 * Used only when the real Von weights cannot run (no exported ONNX graph), so
 * the feature answers instead of failing. Deliberately conservative: genuine
 * knowledge questions return null, and the caller answers with a low-confidence
 * default rather than pretending to know.
 */
object VonDecisionRules {

    data class Verdict(val yes: Boolean, val confidence: Float)

    private val STRONG_NO = listOf(
        Regex("ではない"), Regex("じゃない"), Regex("ではなく"), Regex("わけがない"),
        Regex("違う"), Regex("いいえ"), Regex("うそ"),
        Regex("\\bnot\\b", RegexOption.IGNORE_CASE),
        Regex("n't\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnever\\b", RegexOption.IGNORE_CASE),
        Regex("\\bno\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfalse\\b", RegexOption.IGNORE_CASE)
    )

    private val STRONG_YES = listOf(
        Regex("^はい"), Regex("、はい"), Regex("そうです"), Regex("そうだ"),
        Regex("\\byes\\b", RegexOption.IGNORE_CASE),
        Regex("\\btrue\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcorrect\\b", RegexOption.IGNORE_CASE)
    )

    /** A question needs the real model; heuristics do not know facts. */
    private val QUESTION = Regex("(?:[？?]|か[。.]?)\\s*$")

    private val DECLARATIVE_YES = listOf("です", "ます", "である", "だ")

    /**
     * Yes/No for [text], or null when no lexical signal fires (the caller then
     * answers with [default] at low confidence).
     */
    fun decide(text: String): Verdict? {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return null

        STRONG_NO.firstOrNull { it.containsMatchIn(normalized) }?.let { return Verdict(false, 0.9f) }
        STRONG_YES.firstOrNull { it.containsMatchIn(normalized) }?.let { return Verdict(true, 0.9f) }

        if (QUESTION.containsMatchIn(normalized)) return null

        if (DECLARATIVE_YES.any { normalized.endsWith(it) }) return Verdict(true, 0.65f)
        return null
    }

    /** The verdict used when nothing fires at all. */
    fun default(): Verdict = Verdict(false, 0.5f)

    /**
     * Probability (0..1) that "A holds of B", judged lexically over a
     * "A → B" pair. This is the pair-aware reading of [decide]: a negation
     * inside the pair *lowers* the probability (the pair asserts A is not B),
     * an affirmation raises it, and anything else stays at 0.5 — the honest
     * "cannot say", which the verdict scale reports as C (Not Clear).
     */
    fun holdsProbability(pairText: String): Float {
        val normalized = pairText.trim().lowercase()
        return when {
            normalized.isEmpty() -> 0.5f
            STRONG_NO.any { it.containsMatchIn(normalized) } -> 0.1f
            STRONG_YES.any { it.containsMatchIn(normalized) } -> 0.9f
            else -> 0.5f
        }
    }
}
