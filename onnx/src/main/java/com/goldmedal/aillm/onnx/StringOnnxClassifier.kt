package com.goldmedal.aillm.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.extensions.OrtxPackage
import org.json.JSONArray
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp

/**
 * Runs a Hugging Face sequence-classification model that was exported as a
 * *single* ONNX file with its tokenizer baked into the graph.
 *
 * The point of that export (see `onnx/export/export_tokenizer_model.py`) is that
 * nothing here has to know how the text is tokenized: the model's only input is
 * a `tensor(string)` and its output is the raw Float logits. Feeding it is one
 * `String`, and there is no Kotlin tokenizer to keep in sync with the model.
 *
 * All inference is synchronous and serialised with `@Synchronized` — ONNX
 * Runtime sessions are thread-safe, but keeping one text in flight at a time
 * makes the first-call latency predictable on a phone.
 *
 * Usage:
 * ```
 * val scorer = StringOnnxClassifier.fromFiles("/data/.../laya.onnx").getOrThrow()
 * val label = scorer.best("I would like a refund") // "refund" to 0.91
 * scorer.close()
 * ```
 */
class StringOnnxClassifier(
    val modelPath: String,
    /** Class names in logit order. Empty means "use the index as the name". */
    val labels: List<String> = emptyList()
) : Closeable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var session: OrtSession? = null
    private var textInputName: String? = null
    private var scoreOutputName: String? = null
    private val closed = AtomicBoolean(false)

    val isOpen: Boolean get() = session != null

    /** The single string input, as named in the exported graph. */
    val inputName: String? get() = textInputName

    /** The Float output the scores are read from (usually `logits`). */
    val outputName: String? get() = scoreOutputName

    /**
     * Loads the model. Safe to call more than once; the second call is a no-op.
     */
    @Synchronized
    fun open(): Result<Unit> {
        if (closed.get()) {
            return Result.failure(IllegalStateException("This classifier has been closed."))
        }
        if (session != null) return Result.success(Unit)

        return runCatching {
            val file = File(modelPath)
            check(file.isFile && file.length() > 0L) { "ONNX model not found: $modelPath" }

            val options = OrtSession.SessionOptions().apply {
                // The graph contains onnxruntime-extensions operators such as
                // BertTokenizer; without registering the library the session
                // cannot resolve them.
                registerCustomOpLibrary(OrtxPackage.getLibraryPath())
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val created = environment.createSession(file.absolutePath, options)

            val input = created.inputInfo.entries
                .firstOrNull { (it.value.info as? TensorInfo)?.type == OnnxJavaType.STRING }
                ?.key
                ?: created.inputNames.firstOrNull()
                ?: error("The ONNX model has no inputs.")

            val output = created.outputInfo.entries
                .firstOrNull { (it.value.info as? TensorInfo)?.type == OnnxJavaType.FLOAT }
                ?.key
                ?: created.outputNames.firstOrNull()
                ?: error("The ONNX model has no Float output.")

            session = created
            textInputName = input
            scoreOutputName = output
        }
    }

    /**
     * Raw logits for [text], in label order.
     *
     * The graph is exported without a Softmax so callers can decide what to do
     * with the numbers; [scores] applies one for you.
     */
    @Synchronized
    fun logits(text: String): Result<FloatArray> {
        val active = session
            ?: return Result.failure(IllegalStateException("The model is not loaded."))
        val input = textInputName
            ?: return Result.failure(IllegalStateException("The model is not loaded."))
        val output = scoreOutputName
            ?: return Result.failure(IllegalStateException("The model is not loaded."))

        return runCatching {
            OnnxTensor.createTensor(environment, arrayOf(text), longArrayOf(1)).use { tensor ->
                active.run(mapOf(input to tensor)).use { result ->
                    val outputs = result.toList()
                    val value = outputs.firstOrNull { it.key == output }?.value
                        ?: outputs.first().value
                    flatten(value)
                }
            }
        }
    }

    /** Softmax over [logits], so each value is a probability in `0..1`. */
    fun scores(text: String): Result<FloatArray> = logits(text).map { softmax(it) }

    /** The single winning label and its probability. */
    fun best(text: String): Result<Prediction> = scores(text).map { probabilities ->
        var index = 0
        for (i in probabilities.indices) if (probabilities[i] > probabilities[index]) index = i
        Prediction(label(index), probabilities.getOrElse(index) { 0f }, index)
    }

    /** Every label with its probability, highest first. */
    fun ranked(text: String): Result<List<Prediction>> = scores(text).map { probabilities ->
        probabilities.indices
            .map { Prediction(label(it), probabilities[it], it) }
            .sortedByDescending { it.score }
    }

    fun label(index: Int): String = labels.getOrNull(index) ?: "label_$index"

    override fun close() {
        if (closed.getAndSet(true)) return
        runCatching { session?.close() }
        session = null
        textInputName = null
        scoreOutputName = null
        // The OrtEnvironment is process-wide and shared with anything else that
        // uses ONNX Runtime, so it is deliberately left open.
    }

    private fun flatten(value: OnnxValue): FloatArray {
        val tensor = value as? OnnxTensor
            ?: error("Expected a tensor output but got ${value.javaClass.simpleName}.")
        val buffer = tensor.floatBuffer
        val result = FloatArray(buffer.remaining())
        buffer.get(result)
        return result
    }

    private fun softmax(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        var max = values[0]
        for (v in values) if (v > max) max = v
        var sum = 0.0
        val out = FloatArray(values.size)
        for (i in values.indices) {
            val e = exp((values[i] - max).toDouble())
            out[i] = e.toFloat()
            sum += e
        }
        if (sum <= 0.0) return values
        for (i in out.indices) out[i] = (out[i] / sum).toFloat()
        return out
    }

    /** One class and its probability. */
    data class Prediction(val label: String, val score: Float, val index: Int)

    companion object {
        /**
         * Opens [modelPath] and, when [labelsPath] points at a JSON array, reads
         * the class names the export script wrote alongside the model.
         */
        fun fromFiles(modelPath: String, labelsPath: String? = null): Result<StringOnnxClassifier> {
            val labels = labelsPath?.let(::readLabels).orEmpty()
            val classifier = StringOnnxClassifier(modelPath, labels)
            return classifier.open().map { classifier }
        }

        private fun readLabels(path: String): List<String> = runCatching {
            val file = File(path)
            if (!file.isFile) return emptyList()
            val array = JSONArray(file.readText())
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }
}
