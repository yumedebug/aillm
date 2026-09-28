package com.goldmedal.aillm.onnx

import com.goldmedal.aillm.ai.decision.DecisionDebug
import com.goldmedal.aillm.ai.decision.DecisionFailure
import com.goldmedal.aillm.ai.decision.DecisionFailureKind
import com.goldmedal.aillm.ai.decision.DecisionModel
import com.goldmedal.aillm.ai.decision.DecisionOutcome
import com.goldmedal.aillm.ai.decision.DecisionResult
import com.goldmedal.aillm.ai.decision.DecisionVerdict
import com.goldmedal.aillm.ai.decision.LayaQuestionType
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.onnx.laya.LayaConfig
import com.goldmedal.aillm.onnx.laya.LayaOnnxRuntime
import com.goldmedal.aillm.onnx.laya.LayaPrompt
import com.goldmedal.aillm.onnx.laya.LayaSequence
import com.goldmedal.aillm.onnx.laya.LayaTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.exp

/**
 * The Decision AI: `convaiinnovations/laya-multilingual` on ONNX Runtime.
 *
 * Laya is a non-autoregressive decision model, not a chat model. This class
 * implements the pipeline the model actually defines, end to end:
 *
 * ```
 * State / Question
 *   -> Laya prompt construction   (LayaPrompt)
 *   -> mmBERT BPE tokenizer       (LayaTokenizer)
 *   -> input_ids / attention_mask / marker_pos / marker_mask / qtype
 *   -> ONNX Runtime               (LayaOnnxRuntime)
 *   -> raw logits (one per option marker)
 *   -> temperature calibration    (LayaConfig)
 *   -> softmax -> P(true)
 *   -> Y / N / C
 * ```
 *
 * It never runs Laya through llama.cpp, never treats the model as a text
 * generator, and never falls back to a heuristic: a row that cannot be judged is
 * an explicit [DecisionOutcome.Failed], never a guessed verdict.
 */
class LayaDecisionModel : DecisionModel {

    private var tokenizer: LayaTokenizer? = null
    private var runtime: LayaOnnxRuntime? = null
    private var config: LayaConfig = LayaConfig()

    override val name: String get() = "laya-multilingual · onnx"

    override val isLoaded: Boolean get() = runtime?.isOpen == true && tokenizer != null

    override suspend fun load(
        spec: ModelSpec,
        modelPath: String,
        projectorPath: String?
    ): Result<Unit> = withContext(Dispatchers.Default) {
        unload()

        // Everything Laya needs sits next to the weights in the model directory:
        // the exported graph (the main file), the tokenizer, its config, and the
        // calibration file.
        val directory = File(modelPath).parentFile
            ?: return@withContext Result.failure(IllegalStateException("Laya has no model directory."))

        val graph = LayaOnnxRuntime.EXPORT_NAMES
            .map { File(directory, it) }
            .firstOrNull { it.isFile && it.length() > 0L }
            ?: return@withContext Result.failure(
                IllegalStateException(
                    "Laya's ONNX graph is missing; expected one of ${LayaOnnxRuntime.EXPORT_NAMES} in $directory."
                )
            )

        val tokenizerFile = File(directory, TOKENIZER_NAME)
        if (!tokenizerFile.isFile) {
            return@withContext Result.failure(
                IllegalStateException("Laya's $TOKENIZER_NAME is missing from $directory.")
            )
        }

        runCatching {
            config = File(directory, CONFIG_NAME)
                .takeIf { it.isFile }
                ?.let(LayaConfig::fromFile)
                ?: LayaConfig()
            tokenizer = LayaTokenizer.fromFiles(tokenizerFile, File(directory, TOKENIZER_CONFIG_NAME))
            runtime = LayaOnnxRuntime(graph).also { it.open().getOrThrow() }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                unload()
                Result.failure(error)
            }
        )
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching { runtime?.close() }
        runCatching { tokenizer?.close() }
        runtime = null
        tokenizer = null
        config = LayaConfig()
        Result.success(Unit)
    }

    override suspend fun decide(
        states: List<String>,
        question: String,
        questionType: LayaQuestionType
    ): Result<List<DecisionOutcome>> = withContext(Dispatchers.Default) {
        val activeRuntime = runtime
        val activeTokenizer = tokenizer
        val trimmedQuestion = question.trim()
        val subjects = states.map { it.trim() }

        when {
            activeRuntime == null || activeTokenizer == null ->
                Result.failure(IllegalStateException(DecisionFailureKind.MODEL_NOT_LOADED.label))
            subjects.isEmpty() ->
                Result.failure(IllegalArgumentException("State を入力してください。"))
            trimmedQuestion.isEmpty() ->
                Result.failure(IllegalArgumentException("Question を入力してください。"))
            else -> runCatching {
                judge(activeRuntime, activeTokenizer, subjects, trimmedQuestion, questionType)
            }
        }
    }

    private fun judge(
        runtime: LayaOnnxRuntime,
        tokenizer: LayaTokenizer,
        subjects: List<String>,
        question: String,
        questionType: LayaQuestionType
    ): List<DecisionOutcome> {
        val sequences = subjects.map { subject ->
            LayaPrompt.build(
                tokenizer = tokenizer,
                state = subject,
                instructions = question,
                questionType = questionType,
                config = config
            )
        }

        val logits = runtime.run(sequences)
        return subjects.mapIndexed { row, subject ->
            scoreRow(
                subject = subject,
                question = question,
                questionType = questionType,
                sequence = sequences[row],
                logits = logits.values,
                rowOffset = row * logits.markerCount
            )
        }
    }

    private fun scoreRow(
        subject: String,
        question: String,
        questionType: LayaQuestionType,
        sequence: LayaSequence,
        logits: FloatArray,
        rowOffset: Int
    ): DecisionOutcome {
        val validMarkers = sequence.markerMask.count { it }
        if (validMarkers < 2) {
            return DecisionOutcome.Failed(
                subject,
                DecisionFailure(
                    DecisionFailureKind.MISSING_MARKER,
                    "only $validMarkers option markers survived prompt construction"
                )
            )
        }
        if (rowOffset + validMarkers > logits.size) {
            return DecisionOutcome.Failed(
                subject,
                DecisionFailure(DecisionFailureKind.LOGITS_MISSING, "logits shorter than ${rowOffset + validMarkers}")
            )
        }

        val rawLogits = FloatArray(validMarkers) { logits[rowOffset + it] }
        if (rawLogits.any { it.isNaN() || it.isInfinite() }) {
            return DecisionOutcome.Failed(
                subject,
                DecisionFailure(DecisionFailureKind.INVALID_PROBABILITY, "non-finite logits")
            )
        }

        val temperature = config.temperatureFor(questionType, validMarkers)
        val probabilities = softmax(rawLogits, temperature)

        if (probabilities.any { it.isNaN() || it.isInfinite() || it < 0f || it > 1f }) {
            return DecisionOutcome.Failed(
                subject,
                DecisionFailure(
                    DecisionFailureKind.INVALID_PROBABILITY,
                    "softmax produced an out-of-range probability"
                )
            )
        }

        // For noul, the true option is the second one; the reference reports
        // P(true) as p[1].
        val pTrue = probabilities.getOrElse(TRUE_INDEX) { Float.NaN }
        if (pTrue.isNaN()) {
            return DecisionOutcome.Failed(
                subject,
                DecisionFailure(DecisionFailureKind.INVALID_PROBABILITY, "no true-option probability")
            )
        }

        val debug = DecisionDebug(
            questionType = questionType,
            subject = subject,
            question = question,
            markerPos = sequence.markerPos.toList(),
            rawLogits = rawLogits.toList(),
            probabilities = probabilities.toList(),
            temperature = temperature,
            temperatureConfig = config.temperature.toList()
        )

        return DecisionOutcome.Answered(
            subject,
            DecisionResult(
                subject = subject,
                question = question,
                questionType = questionType,
                verdict = DecisionVerdict.fromProbability(pTrue),
                probability = pTrue,
                debug = debug
            )
        )
    }

    /**
     * Temperature-scaled softmax over the option logits, the way
     * `laya.agent._decode_answers` does it: `z = logits / t`, then a max-shifted
     * softmax. The temperature is the fitted one, never a hard-coded 1.0.
     */
    private fun softmax(logits: FloatArray, temperature: Float): FloatArray {
        if (logits.isEmpty()) return logits
        val scale = if (temperature > 0f) temperature else 1f
        var max = logits[0] / scale
        val scaled = FloatArray(logits.size) { index -> logits[index] / scale }
        for (value in scaled) if (value > max) max = value
        var sum = 0.0
        for (index in scaled.indices) {
            val exponent = exp((scaled[index] - max).toDouble())
            scaled[index] = exponent.toFloat()
            sum += exponent
        }
        if (sum <= 0.0 || sum.isNaN()) {
            return FloatArray(logits.size) { Float.NaN }
        }
        for (index in scaled.indices) scaled[index] = (scaled[index] / sum).toFloat()
        return scaled
    }

    private companion object {
        const val TOKENIZER_NAME = "tokenizer.json"
        const val TOKENIZER_CONFIG_NAME = "tokenizer_config.json"
        const val CONFIG_NAME = "rl_agent_config.json"

        /** noul orders its two options false, then true. */
        const val TRUE_INDEX = 1
    }
}
