package com.goldmedal.aillm.onnx

import com.goldmedal.aillm.ai.decision.DecisionModel
import com.goldmedal.aillm.ai.decision.DecisionResult
import com.goldmedal.aillm.ai.decision.DecisionSource
import com.goldmedal.aillm.ai.decision.DecisionVerdict
import com.goldmedal.aillm.ai.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.exp

/**
 * The Decision AI: wfzyx/von (ModernBERT-large, 395M, non-autoregressive).
 *
 * Von answers structured decisions in a single encoder pass — no token
 * generation. Each call is one judgment of "A holds of B": the subject and the
 * context go in, one probability comes out, and the probability alone picks
 * the verdict (Y / N / C — see [DecisionVerdict]). Two paths:
 *
 * 1. **Real model** — when a merged string-in / score-out ONNX export of
 *    wfzyx/von (see `onnx/export`) sits next to the downloaded weights, it is
 *    run through [StringOnnxClassifier]: the NLI head is read as
 *    entailment → holds, contradiction → does not hold, with the temperature
 *    the authors fitted in `calibration.json` applied to the logits.
 * 2. **Heuristic fallback** — until that export exists, verdicts come from
 *    [VonDecisionRules] (negation / affirmation patterns), clearly labelled
 *    in the result.
 *
 * Either way [decide] never fails on content: it always answers with a
 * verdict and a probability.
 */
class VonDecisionModel : DecisionModel {

    private var classifier: StringOnnxClassifier? = null
    private var temperature: Float = DEFAULT_TEMPERATURE

    override val name: String
        get() = if (classifier != null) "wfzyx/von · onnx" else "wfzyx/von · heuristic"

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
        temperature = readTemperature(File(dir, CALIBRATION_NAME)) ?: DEFAULT_TEMPERATURE

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
     * One forward pass over "A [SEP] B": does A entail B? The exported head is
     * the NLI head, whose label order (`id2label` in config.json) is
     * entailment / neutral / contradiction. The probability that A holds of B
     * is the entailment share of the two decisive classes, temperature-scaled
     * as fitted by the model's authors — a strong neutral leaves less room for
     * either side and lands the verdict near the C band.
     *
     * Note the model was trained on English text, so a Japanese pair is judged
     * less reliably than an English one.
     */
    private fun modelVerdict(classifier: StringOnnxClassifier, subject: String, context: String): DecisionResult {
        val scores = classifier.logits("$subject $SEP $context").getOrThrow()
        val probabilities = softmax(scores.take(HEAD_SIZE).toFloatArray(), temperature)
        val entailment = probabilities.getOrElse(ENTAILMENT) { 0f }
        val contradiction = probabilities.getOrElse(CONTRADICTION) { 0f }
        val total = entailment + contradiction
        val probability = if (total > 0f) entailment / total else DEFAULT_PROBABILITY
        return DecisionResult(
            subject = subject,
            context = context,
            verdict = DecisionVerdict.fromProbability(probability),
            probability = probability,
            source = DecisionSource.MODEL
        )
    }

    private fun rulesVerdict(subject: String, context: String): DecisionResult {
        // Pair-aware: the probability is read over the whole "A → B" pair, so
        // a negated pair ("A is not B") lowers it instead of affirming.
        val probability = VonDecisionRules.holdsProbability("$subject → $context")
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

    private fun readTemperature(file: File): Float? = runCatching {
        if (!file.isFile) return null
        val value = JSONObject(file.readText()).optDouble("temperature", Double.NaN)
        if (value.isNaN() || value <= 0.0) null else value.toFloat()
    }.getOrNull()

    private companion object {
        val EXPORT_NAMES = listOf("von-classifier.onnx", "von-classifier.int8.onnx", "model.onnx")
        const val LABELS_NAME = "von-classifier.labels.json"
        const val CALIBRATION_NAME = "calibration.json"

        /** The separator between the subject (A) and the context (B). */
        const val SEP = "[SEP]"

        const val HEAD_SIZE = 3
        const val ENTAILMENT = 0
        const val CONTRADICTION = 2
        const val DEFAULT_TEMPERATURE = 1f
        const val DEFAULT_PROBABILITY = 0.5f

        const val FALLBACK_NOTE =
            "Von's weights are installed, but running them on-device needs a single-file ONNX " +
                "export (see onnx/export). Until that file is present, decisions use the " +
                "built-in heuristic fallback."
    }
}
