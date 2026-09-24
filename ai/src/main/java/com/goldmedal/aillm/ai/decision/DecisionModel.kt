package com.goldmedal.aillm.ai.decision

import com.goldmedal.aillm.ai.engine.OnDeviceEngine

/** The id of the wfzyx/von entry in the model catalogue. */
const val VON_MODEL_ID = "wfzyx-von"

/** How a verdict was produced. */
enum class DecisionSource {
    /** The real wfzyx/von weights ran on-device (single-file ONNX export). */
    MODEL,

    /** The built-in lexical fallback answered instead. */
    HEURISTIC
}

/**
 * The three-way verdict scale.
 *
 * The probability Von reports for "A holds of B" is cut into:
 *
 * ```
 * probability >= 51%          -> Y
 * probability <= 49%          -> N
 * 49% < probability < 51%     -> C (Not Clear)
 * ```
 */
enum class DecisionVerdict {
    Y,
    N,
    C;

    companion object {
        /** The band's edges, in percent. Anything between 49 and 51 is not clear. */
        const val YES_THRESHOLD = 51f
        const val NO_THRESHOLD = 49f

        fun fromProbability(probability: Float): DecisionVerdict = when {
            probability >= YES_THRESHOLD / 100f -> Y
            probability <= NO_THRESHOLD / 100f -> N
            else -> C
        }
    }
}

data class DecisionResult(
    /** The proposition judged: "A" against the context "B". */
    val subject: String,
    /** The context the subject was judged against (the screen's B field). */
    val context: String = "",
    /** Y, N or C — derived from [probability], never from prose. */
    val verdict: DecisionVerdict,
    /** 0..1 probability that A holds of B. */
    val probability: Float,
    val source: DecisionSource,
    /** Present when the answer did not come from the real model. */
    val note: String? = null
)

/**
 * The Decision AI: wfzyx/von, a non-autoregressive "System One" model that
 * judges whether "A is B" holds, in a single forward pass.
 *
 * Unlike the chat engine it never streams text and never takes a conversation:
 * a proposition in, a probability out, and the probability — not the model —
 * decides Y, N or C. It runs on its own runtime (:onnx), so loading it does
 * not evict the resident chat model.
 */
interface DecisionModel : OnDeviceEngine {
    suspend fun decide(subject: String, context: String): Result<DecisionResult>
}
