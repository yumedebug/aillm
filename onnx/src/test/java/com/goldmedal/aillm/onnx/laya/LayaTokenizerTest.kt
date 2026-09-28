package com.goldmedal.aillm.onnx.laya

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

class LayaTokenizerTest {

    private val fixture = """
        {
          "added_tokens": [
            {"id": 0, "content": "<pad>", "special": true},
            {"id": 1, "content": "<eos>", "special": true},
            {"id": 2, "content": "<bos>", "special": true},
            {"id": 3, "content": "<unk>", "special": true},
            {"id": 4, "content": "<mask>", "special": true},
            {"id": 5, "content": "<0x41>", "special": false},
            {"id": 6, "content": "<0x42>", "special": false}
          ],
          "model": {
            "type": "BPE",
            "byte_fallback": true,
            "vocab": {
              "<pad>": 0, "<eos>": 1, "<bos>": 2, "<unk>": 3, "<mask>": 4,
              "<0x41>": 5, "<0x42>": 6,
              "\u2581": 10, "a": 11, "b": 12, "\u2581a": 13, "\u2581b": 14, "ab": 15, "\u2581ab": 16
            },
            "merges": [["a", "b"], ["\u2581", "ab"], ["\u2581", "a"], ["\u2581", "b"]]
          }
        }
    """.trimIndent()

    private fun fixtureTokenizer(): LayaTokenizer =
        LayaTokenizer.fromReader(StringReader(fixture))

    @Test
    fun `resolves special tokens from added_tokens`() {
        val tokenizer = fixtureTokenizer()
        assertEquals(2, tokenizer.clsTokenId)
        assertEquals(1, tokenizer.sepTokenId)
        assertEquals(4, tokenizer.maskTokenId)
        assertEquals(3, tokenizer.unkTokenId)
        assertEquals(0, tokenizer.padTokenId)
        assertEquals("<mask>", tokenizer.maskTokenText)
    }

    @Test
    fun `prepends the metaspace and merges by rank`() {
        val tokenizer = fixtureTokenizer()
        // "ab" -> "▁ab": ▁+a is rank 2, a+b is rank 0 (merged first), then ▁+ab.
        assertArrayEquals(intArrayOf(16), tokenizer.encode("ab"))
    }

    @Test
    fun `splits on the metaspace boundary`() {
        val tokenizer = fixtureTokenizer()
        assertArrayEquals(intArrayOf(13, 14), tokenizer.encode("a b"))
        assertArrayEquals(intArrayOf(13), tokenizer.encode("a"))
    }

    @Test
    fun `falls back to byte tokens for unknown code points`() {
        val tokenizer = fixtureTokenizer()
        // "A" is not in the vocabulary; its UTF-8 byte 0x41 maps to <0x41> (id 5).
        assertArrayEquals(intArrayOf(10, 5), tokenizer.encode("A"))
    }

    @Test
    fun `empty input encodes to no tokens`() {
        assertArrayEquals(intArrayOf(), fixtureTokenizer().encode(""))
    }

    // ------------------------------------------------------- real-tokenizer checks

    private fun realTokenizerFile(): File? {
        val configured = System.getenv("LAYA_TOKENIZER") ?: return File("/tmp/laya-tok.json").takeIf { it.isFile }
        return File(configured).takeIf { it.isFile }
    }

    @Test
    fun `matches the reference tokenizer on golden vectors`() {
        val file = realTokenizerFile()
        assumeTrue("Set LAYA_TOKENIZER to the mmBERT tokenizer.json to run this test", file != null)

        val tokenizer = LayaTokenizer.fromFiles(file!!)
        assertEquals(2, tokenizer.clsTokenId)
        assertEquals(1, tokenizer.sepTokenId)
        assertEquals(4, tokenizer.maskTokenId)

        assertArrayEquals(intArrayOf(201202), tokenizer.encode("東京都"))
        assertArrayEquals(intArrayOf(235248, 207728), tokenizer.encode("埼玉県"))
        assertArrayEquals(intArrayOf(8595, 235505, 142355, 39021, 236598), tokenizer.encode("カリフォルニア州"))
        assertArrayEquals(intArrayOf(26867, 116296, 235443, 235544), tokenizer.encode("日本のものか？"))
        assertArrayEquals(intArrayOf(714, 4320, 8426, 25341), tokenizer.encode("The quick brown fox"))
        assertArrayEquals(intArrayOf(552, 6311, 2872, 235292), tokenizer.encode("noul question:"))
        assertArrayEquals(intArrayOf(25612, 2134), tokenizer.encode("hello world"))
        // A code point outside the vocabulary becomes its UTF-8 byte tokens.
        assertArrayEquals(intArrayOf(235248, 457, 386, 401, 406), tokenizer.encode("𩸽"))
    }
}
