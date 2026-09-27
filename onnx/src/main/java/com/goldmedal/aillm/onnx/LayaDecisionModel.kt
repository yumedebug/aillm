package com.goldmedal.aillm.onnx

import com.goldmedal.aillm.ai.decision.DecisionModel
import com.goldmedal.aillm.ai.decision.DecisionResult
import com.goldmedal.aillm.ai.decision.DecisionSource
import com.goldmedal.aillm.ai.decision.DecisionVerdict
import com.goldmedal.aillm.ai.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.exp

/**
 * The Decision AI: convaiinnovations/laya-multilingual (mmBERT-base, 322M,
 * non-autoregressive).
 *
 * Laya answers structured decisions in a single encoder pass — no token
 * generation. Each call is one judgment of "A holds of B": the subject and the
 * context go in, one probability comes out, and the probability alone picks
 * the verdict (Y / N / C — see [DecisionVerdict]). Two paths:
 *
 * 1. **Real model** — when a merged string-in / score-out ONNX export of
 *    `convaiinnovations/laya-multilingual` (see `onnx/export`) sits next to the
 *    downloaded weights, it is run through [StringOnnxClassifier]: the decision
 *    head's `noul` question ("does B hold of A?") is read as a probability over
 *    false/true, with the temperature from the checkpoint's `rl_agent_config.json`
 *    applied to the logits.
 * 2. **Heuristic fallback** — until that export exists, verdicts come from
 *    [LayaDecisionRules] (negation / affirmation patterns), clearly labelled
 *    in the result.
 *
 * Either way [decide] never fails on content: it always answers with a
 * verdict and a probability.
 *
 * Laya covers 100+ languages, so unlike the previous English-only checkpoint a
 * Japanese pair is judged on the model's own terms rather than by a fallback.
 */
class LayaDecisionModel : DecisionModel {

    private var classifier: StringOnnxClassifier? = null
    private var temperature: Float = DEFAULT_TEMPERATURE

    override val name: String
        get() = if (classifier != null) "laya-multilingual · onnx" else "laya-multilingual · heuristic"

    override val isLoaded: Boolean get() = classifier != null

    override suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String?
    ): Result<Unit> = withContext(Dispatchers.Default) {
        classifier?.close()
        classifier = null
        temperature = DEFAULT_TEMPERATURE

        // The downloader stores the original HF files (safetensors weights plus
        // tokenizer and config) in one directory. A single-file ONNX export of
        // the same model is what can actually execute here; it is looked up by
        // its conventional file name.
        val dir = File(modelPath).parentFile
        val labelsPath = File(dir, LABELS_NAME).takeIf { it.isFile }?.absolutePath
        classifier = EXPORT_NAMES.asSequence()
            .map { File(dir, it) }
            .firstOrNull { it.isFile && it.length() > 0L }
            ?.let { file -> StringOnnxClassifier.fromFiles(file.absolutePath, labelsPath).getOrNull() }

        // The fitted temperature is part of the model rather than a detail worth
        // guessing, so it is read from the file that shipped with the weights.
        temperature = readTemperature(File(dir, AGENT_CONFIG_NAME)) ?: DEFAULT_TEMPERATURE

        Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.Default) {
        classifier?.close()
        classifier = null
        Result.success(Unit)
    }

    /**
     * Judges whether "[subject] holds of [context]" — the screen's A against B.
     * A verdict never fails on content: the engine answers with a probability
     * and says whether the real model or the built-in fallback produced it.
     */
    override suspend fun decide(subject: String, context: String): Result<DecisionResult> =
        withContext(Dispatchers.Default) {
            val a = subject.trim()
            val b = context.trim()
            if (a.isEmpty() || b.isEmpty()) {
                Result.failure(IllegalArgumentException("Fill in both A and B first."))
            } else {
                val active = classifier
                val result = if (active != null) {
                    runCatching { modelVerdict(active, a, b) }.getOrElse { rulesVerdict(a, b) }
                } else {
                    rulesVerdict(a, b)
                }
                Result.success(result)
            }
        }

    /**
     * One forward pass over the exported graph, asked the `noul` question
     * "does [context] hold of [subject]?".
     *
     * The head is exported without a softmax. A two-value head is read as
     * [FALSE_INDEX] / [TRUE_INDEX] (the checkpoint orders `noul` answers as
     * false, then true); a single-value head is read with a sigmoid, which is
     * how the `noul` primitive reports P(true). Either way the probability is
     * temperature-scaled as fitted by the model's authors, so an unsure
     * judgment lands the verdict near the C band rather than at a confident
     * extreme.
     */
    private fun modelVerdict(classifier: StringOnnxClassifier, subject: String, context: String): DecisionResult {
        val scores = classifier.logits(questionFor(subject, context)).getOrThrow()
        val probability = probabilityFrom(scores)
        return DecisionResult(
            subject = subject,
            context = context,
            verdict = DecisionVerdict.fromProbability(probability),
            probability = probability,
            source = DecisionSource.MODEL
        )
    }

    private fun probabilityFrom(scores: FloatArray): Float = when {
        scores.isEmpty() -> DEFAULT_PROBABILITY
        scores.size == 1 -> sigmoid(scores[0] / temperature)
        else -> softmax(scores.take(BINARY_HEAD_SIZE).toFloatArray(), temperature)[TRUE_INDEX]
    }

    private fun sigmoid(value: Float): Float =
        (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()

    private fun rulesVerdict(subject: String, context: String): DecisionResult {
        // Pair-aware: the probability is read over the whole "A → B" pair, so
        // a negated pair ("A is not B") lowers it instead of affirming.
        val probability = LayaDecisionRules.holdsProbability("$subject → $context")
        return DecisionResult(
            subject = subject,
            context = context,
            verdict = DecisionVerdict.fromProbability(probability),
            probability = probability,
            source = DecisionSource.HEURISTIC,
            note = if (classifier == null) {
                FALLBACK_NOTE
            } else {
                "The model output could not be read; the heuristic fallback answered instead."
            }
        )
    }

    /** Temperature-scaled softmax, as fitted by the model's authors. */
    private fun softmax(values: FloatArray, temperature: Float): FloatArray {
        if (values.isEmpty()) return values
        val scale = if (temperature > 0f) temperature else 1f
        var max = values[0] / scale
        val scaled = FloatArray(values.size) { index -> values[index] / scale }
        for (value in scaled) if (value > max) max = value
        var sum = 0.0
        for (index in scaled.indices) {
            val exponent = exp((scaled[index] - max).toDouble())
            scaled[index] = exponent.toFloat()
            sum += exponent
        }
        if (sum <= 0.0) return values
        for (index in scaled.indices) scaled[index] = (scaled[index] / sum).toFloat()
        return scaled
    }

    /**
     * The temperature the checkpoint carries in `rl_agent_config.json`, which
     * holds one value per question type (`temperature = [1.0, 1.0, 1.0]`). The
     * model ships uncalibrated, so the first value is the honest default; the
     * array form and the scalar form are both accepted.
     */
    private fun readTemperature(file: File): Float? = runCatching {
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        when (val raw = json.opt("temperature")) {
            is JSONArray -> if (raw.length() > 0) raw.optDouble(0, Double.NaN) else Double.NaN
            is Number -> raw.toDouble()
            else -> Double.NaN
        }.takeIf { !it.isNaN() && it > 0.0 }?.toFloat()
    }.getOrNull()

    private companion object {
        val EXPORT_NAMES = listOf(
            "laya-classifier.onnx",
            "laya-classifier.int8.onnx",
            "model.onnx"
        )
        const val LABELS_NAME = "laya-classifier.labels.json"
        const val AGENT_CONFIG_NAME = "rl_agent_config.json"

        /** The separator between the subject (A) and the context (B). */
        const val SEP = "[SEP]"

        /** How many logits the binary decision head is read over. */
        const val BINARY_HEAD_SIZE = 2

        /** `noul` orders its two classes false, then true. */
        const val TRUE_INDEX = 1
        const val DEFAULT_TEMPERATURE = 1f
        const val DEFAULT_PROBABILITY = 0.5f

        const val FALLBACK_NOTE =
            "Laya's weights are installed, but running them on-device needs a single-file ONNX " +
                "export (see onnx/export). Until that file is present, decisions use the " +
                "built-in heuristic fallback."

        /** The `noul` question: "does [context] hold of [subject]?" */
        fun questionFor(subject: String, context: String): String = "$subject $SEP $context"
    }
}
