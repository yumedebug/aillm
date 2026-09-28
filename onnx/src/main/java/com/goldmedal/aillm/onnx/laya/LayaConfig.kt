package com.goldmedal.aillm.onnx.laya

import com.goldmedal.aillm.ai.decision.LayaQuestionType
import java.io.BufferedReader
import java.io.File
import java.io.Reader

/**
 * The checkpoint's `rl_agent_config.json`.
 *
 * This is where Laya's fitted temperature lives. It is part of the model, not a
 * tuning knob the app is allowed to invent: the app must not pin
 * `temperature = 1.0` because that would publish the model's raw logits as
 * probabilities.
 *
 * The temperature is looked up per `(question type, option count)` bucket and
 * clamped to `[0.5, 5.0]`, exactly as `laya.common.clamp_temperature` /
 * `laya.agent.temp_bucket` do. A bucket below 1 sharpens the logits ~10x and
 * would turn a coin flip into a certainty, so it is refused.
 */
data class LayaConfig(
    /** The longest token sequence Laya will accept (`max_len`). */
    val maxLen: Int = DEFAULT_MAX_LEN,
    /** How many tokens the question head and its options may use (`head_max_len`). */
    val headMaxLen: Int = DEFAULT_HEAD_MAX_LEN,
    /** One temperature per question type, indexed by [LayaQuestionType.qtype]. */
    val temperature: FloatArray = floatArrayOf(1f, 1f, 1f),
    /** Fitted temperatures for specific `"<type>:<option-count>"` buckets. */
    val temperatureByOptions: Map<String, Float> = emptyMap()
) {

    /**
     * The temperature to divide this question's logits by:
     * the `(type, option count)` bucket when it is fitted, otherwise the
     * per-type temperature — both clamped and NaN-safe.
     */
    fun temperatureFor(questionType: LayaQuestionType, optionCount: Int): Float {
        val bucket = bucketKey(questionType, optionCount)
        return clampTemperature(temperatureByOptions[bucket] ?: temperature.getOrElse(questionType.qtype) { 1f })
    }

    val isCalibrated: Boolean
        get() = temperatureByOptions.isNotEmpty() ||
            temperature.any { it != 1f }

    companion object {
        const val DEFAULT_MAX_LEN = 1024
        const val DEFAULT_HEAD_MAX_LEN = 256

        /** No honest calibration sharpens harder than this (see `clamp_temperature`). */
        const val TEMP_MIN = 0.5f
        const val TEMP_MAX = 5.0f

        /** `"2"`, `"3-5"`, `"6-10"`, `"11+"` — the option-count buckets Laya fits. */
        fun optionBucket(optionCount: Int): String = when {
            optionCount <= 2 -> "2"
            optionCount <= 5 -> "3-5"
            optionCount <= 10 -> "6-10"
            else -> "11+"
        }

        fun bucketKey(questionType: LayaQuestionType, optionCount: Int): String =
            "${questionType.key}:${optionBucket(optionCount)}"

        /** Confines a temperature to `[0.5, 5.0]`, falling back to 1.0 for NaN/inf. */
        fun clampTemperature(value: Float): Float = when {
            value.isNaN() || value.isInfinite() -> 1f
            value < TEMP_MIN -> TEMP_MIN
            value > TEMP_MAX -> TEMP_MAX
            else -> value
        }

        fun fromFile(file: File): LayaConfig =
            file.bufferedReader().use { fromReader(it) }

        fun fromReader(reader: Reader): LayaConfig {
            var maxLen = DEFAULT_MAX_LEN
            var headMaxLen = DEFAULT_HEAD_MAX_LEN
            var temperature = floatArrayOf(1f, 1f, 1f)
            var byOptions: Map<String, Float> = emptyMap()

            JsonStream(BufferedReader(reader)).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "max_len" -> maxLen = json.nextInt()
                        "head_max_len" -> headMaxLen = json.nextInt()
                        "temperature" -> temperature = readTemperature(json)
                        "temperature_by_options" -> byOptions = readTemperatureByOptions(json)
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            return LayaConfig(maxLen, headMaxLen, temperature, byOptions)
        }

        private fun readTemperature(json: JsonStream): FloatArray {
            val values = ArrayList<Float>(3)
            json.beginArray()
            while (json.hasNext()) values.add(clampTemperature(json.nextFloatValue()))
            json.endArray()
            // The shipped array is indexed by qtype; keep three slots even if the
            // file declares fewer.
            val out = FloatArray(3) { 1f }
            for (index in 0 until minOf(3, values.size)) out[index] = values[index]
            return out
        }

        private fun readTemperatureByOptions(json: JsonStream): Map<String, Float> {
            val values = HashMap<String, Float>()
            json.beginObject()
            while (json.hasNext()) {
                val key = json.nextName()
                values[key] = clampTemperature(json.nextFloatValue())
            }
            json.endObject()
            return values
        }

        private fun JsonStream.nextFloatValue(): Float = nextDoubleValue().toFloat()

        private fun JsonStream.nextDoubleValue(): Double {
            // JsonStream exposes ints/longs; temperatures are decimals, so read
            // the raw token as a string and parse it.
            val raw = nextNumberString()
            return raw.toDoubleOrNull() ?: 1.0
        }
    }
}
