package com.goldmedal.aillm.onnx.laya

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class JsonStreamTest {

    @Test
    fun `reads nested objects, arrays and scalars`() {
        val json = """{"a": 1, "b": [true, false, null], "c": {"d": "x\ny"}}"""
        JsonStream(StringReader(json)).use { stream ->
            stream.beginObject()
            assertTrue(stream.hasNext())
            assertEquals("a", stream.nextName())
            assertEquals(1, stream.nextInt())
            assertEquals("b", stream.nextName())
            stream.beginArray()
            assertTrue(stream.nextBoolean())
            assertFalse(stream.nextBoolean())
            stream.skipValue()
            stream.endArray()
            assertEquals("c", stream.nextName())
            stream.beginObject()
            assertEquals("d", stream.nextName())
            assertEquals("x\ny", stream.nextString())
            stream.endObject()
            assertFalse(stream.hasNext())
            stream.endObject()
        }
    }

    @Test
    fun `parses escaped and unicode strings`() {
        val json = "{\"k\": \"a\\\"b\\\\c\\u2581\"}"
        JsonStream(StringReader(json)).use { stream ->
            stream.beginObject()
            assertEquals("k", stream.nextName())
            assertEquals("a\"b\\c\u2581", stream.nextString())
            stream.endObject()
        }
    }

    @Test
    fun `reads decimal numbers as raw text`() {
        val json = """{"t": [1.0, 0.5, 2]}"""
        JsonStream(StringReader(json)).use { stream ->
            stream.beginObject()
            assertEquals("t", stream.nextName())
            stream.beginArray()
            assertEquals(1.0, stream.nextNumberString().toDouble(), 0.0)
            assertEquals(0.5, stream.nextNumberString().toDouble(), 0.0)
            assertEquals(2, stream.nextInt())
            stream.endArray()
            stream.endObject()
        }
    }

    @Test
    fun `skips deeply nested values`() {
        val json = """{"keep": "yes", "drop": {"a": [1, {"b": [true, null]}, "c"]}}"""
        JsonStream(StringReader(json)).use { stream ->
            stream.beginObject()
            assertEquals("keep", stream.nextName())
            assertEquals("yes", stream.nextString())
            assertEquals("drop", stream.nextName())
            stream.skipValue()
            assertFalse(stream.hasNext())
            stream.endObject()
        }
    }
}
