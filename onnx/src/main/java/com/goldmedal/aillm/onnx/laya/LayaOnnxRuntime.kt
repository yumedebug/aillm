package com.goldmedal.aillm.onnx.laya

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Laya ONNX session.
 *
 * The exported graph (`laya-mlx export-onnx`, or a compatible mirror) declares
 * exactly five inputs and returns the per-option decision logits:
 *
 * ```
 * input_ids     int64 [batch, sequence]
 * attention_mask int64 [batch, sequence]
 * marker_pos    int64 [batch, markers]   // index of each option's [MASK]
 * marker_mask   bool  [batch, markers]   // which markers are real
 * qtype         int64 [batch]            // 0 choice, 1 score, 2 noul
 * -> logits     float32 [batch, markers]
 * ```
 *
 * A whole batch of states asked the same question is one forward pass: the
 * sequences are padded to the batch's longest, and the marker tensors to the
 * batch's widest question. This is the batching the spec calls for, and it is
 * why [run] takes a list.
 *
 * ONNX Runtime sessions are thread-safe, but a phone gains nothing from two
 * concurrent encoders, so calls are serialised.
 */
class LayaOnnxRuntime(private val modelFile: File) : Closeable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val closed = AtomicBoolean(false)

    private var session: OrtSession? = null
    private var inputNames: InputNames? = null
    private var logitsOutputName: String? = null

    val isOpen: Boolean get() = session != null

    private data class InputNames(
        val inputIds: String,
        val attentionMask: String,
        val markerPos: String,
        val markerMask: String,
        val qtype: String
    )

    /** Opens the graph. Safe to call more than once. */
    @Synchronized
    fun open(): Result<Unit> {
        if (closed.get()) return Result.failure(IllegalStateException("This Laya runtime has been closed."))
        if (session != null) return Result.success(Unit)

        return runCatching {
            check(modelFile.isFile && modelFile.length() > 0L) { "ONNX model not found: $modelFile" }
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val created = environment.createSession(modelFile.absolutePath, options)

            val declared = created.inputNames
            fun require(name: String): String =
                declared.firstOrNull { it == name }
                    ?: error("Laya graph is missing the '$name' input; found $declared")

            val logitsName = created.outputInfo.entries
                .firstOrNull { it.key == "logits" }
                ?.key
                ?: created.outputInfo.entries
                    .firstOrNull { (it.value.info as? TensorInfo)?.type == OnnxJavaType.FLOAT }
                    ?.key
                ?: error("Laya graph has no float logits output; found ${created.outputNames}")

            session = created
            inputNames = InputNames(
                inputIds = require("input_ids"),
                attentionMask = require("attention_mask"),
                markerPos = require("marker_pos"),
                markerMask = require("marker_mask"),
                qtype = require("qtype")
            )
            logitsOutputName = logitsName
        }
    }

    /** Raw decision logits for a batch of examples: `[rows * markerCount]`. */
    data class Logits(val values: FloatArray, val markerCount: Int)

    /**
     * Runs one forward pass over [sequences]. Every sequence must have been built
     * for the same question, so the marker count (option count) is shared.
     */
    @Synchronized
    fun run(sequences: List<LayaSequence>): Logits {
        val active = session ?: error("Laya ONNX model is not loaded.")
        val names = inputNames ?: error("Laya ONNX model is not loaded.")
        val logitsName = logitsOutputName ?: error("Laya ONNX model is not loaded.")
        require(sequences.isNotEmpty()) { "Laya was asked to run an empty batch." }

        val rows = sequences.size
        val sequenceLength = sequences.maxOf { it.length }
        val markerCount = sequences.maxOf { it.markerCount }
        require(markerCount > 0) { "Laya sequences carry no option markers." }

        val inputIds = LongArray(rows * sequenceLength)
        val attention = LongArray(rows * sequenceLength)
        val markerPos = LongArray(rows * markerCount)
        val markerMask = Array(rows) { BooleanArray(markerCount) }
        val qtype = LongArray(rows)

        for (row in 0 until rows) {
            val sequence = sequences[row]
            qtype[row] = sequence.qtype.toLong()
            for (column in 0 until sequenceLength) {
                val offset = row * sequenceLength + column
                inputIds[offset] = sequence.inputIds.getOrElse(column) { 0 }.toLong()
                attention[offset] = if (column < sequence.length) 1L else 0L
            }
            for (marker in 0 until markerCount) {
                markerPos[row * markerCount + marker] =
                    sequence.markerPos.getOrElse(marker) { 0 }.toLong()
                markerMask[row][marker] = sequence.markerMask.getOrElse(marker) { false }
            }
        }

        val shapeSeq = longArrayOf(rows.toLong(), sequenceLength.toLong())
        val shapeMarkers = longArrayOf(rows.toLong(), markerCount.toLong())

        return OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), shapeSeq).use { ids ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(attention), shapeSeq).use { mask ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(markerPos), shapeMarkers).use { positions ->
                    OnnxTensor.createTensor(environment, markerMask).use { markers ->
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(qtype), longArrayOf(rows.toLong())).use { types ->
                            val inputs = mapOf(
                                names.inputIds to ids,
                                names.attentionMask to mask,
                                names.markerPos to positions,
                                names.markerMask to markers,
                                names.qtype to types
                            )
                            active.run(inputs).use { result ->
                                val value = result.toList()
                                    .firstOrNull { it.key == logitsName }?.value
                                    ?: result.toList().first().value
                                val tensor = value as? OnnxTensor
                                    ?: error("Laya logits output was ${value.javaClass.simpleName}, not a tensor.")
                                val buffer = tensor.floatBuffer
                                val out = FloatArray(buffer.remaining())
                                buffer.get(out)
                                check(out.size >= rows * markerCount) {
                                    "Laya returned ${out.size} logits for $rows rows x $markerCount markers."
                                }
                                Logits(out, markerCount)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        runCatching { session?.close() }
        session = null
        inputNames = null
        logitsOutputName = null
    }

    companion object {
        val EXPORT_NAMES = listOf("model.onnx", "laya-multilingual.onnx", "laya-classifier.onnx")
    }
}
