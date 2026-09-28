package com.goldmedal.aillm.onnx.laya

import com.goldmedal.aillm.ai.decision.LayaQuestionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class LayaConfigTest {

    private fun parse(json: String) = LayaConfig.fromReader(StringReader(json))

    @Test
    fun `reads the shipped uncalibrated config`() {
        val config = parse(
            """{"max_len":1024,"head_max_len":256,"temperature":[1.0,1.0,1.0],"temperature_by_options":{}}"""
        )
        assertEquals(1024, config.maxLen)
        assertEquals(256, config.headMaxLen)
        assertEquals(1.0f, config.temperatureFor(LayaQuestionType.NOUL, 2), 0.0f)
        assertEquals(false, config.isCalibrated)
    }

    @Test
    fun `prefers a fitted option-count bucket`() {
        val config = parse(
            """{"temperature":[1.0,1.0,1.0],"temperature_by_options":{"noul:2":3.0}}"""
        )
        assertEquals(3.0f, config.temperatureFor(LayaQuestionType.NOUL, 2), 0.0f)
        // No bucket for a different type falls back to its per-type value.
        assertEquals(1.0f, config.temperatureFor(LayaQuestionType.CHOICE, 5), 0.0f)
    }

    @Test
    fun `clamps temperatures into the honest range`() {
        val config = parse("""{"temperature":[0.1,10.0,1.0]}""")
        assertEquals(LayaConfig.TEMP_MIN, config.temperatureFor(LayaQuestionType.CHOICE, 2), 0.0f)
        assertEquals(LayaConfig.TEMP_MAX, config.temperatureFor(LayaQuestionType.SCORE, 2), 0.0f)
    }

    @Test
    fun `option buckets match the reference`() {
        assertEquals("2", LayaConfig.optionBucket(1))
        assertEquals("2", LayaConfig.optionBucket(2))
        assertEquals("3-5", LayaConfig.optionBucket(3))
        assertEquals("3-5", LayaConfig.optionBucket(5))
        assertEquals("6-10", LayaConfig.optionBucket(10))
        assertEquals("11+", LayaConfig.optionBucket(11))
        assertEquals("noul:2", LayaConfig.bucketKey(LayaQuestionType.NOUL, 2))
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val config = parse("""{}""")
        assertEquals(LayaConfig.DEFAULT_MAX_LEN, config.maxLen)
        assertEquals(LayaConfig.DEFAULT_HEAD_MAX_LEN, config.headMaxLen)
        assertEquals(1.0f, config.temperatureFor(LayaQuestionType.NOUL, 2), 0.0f)
    }
}
