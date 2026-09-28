package com.goldmedal.aillm.onnx.laya

import com.goldmedal.aillm.ai.decision.LayaQuestionType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

class LayaPromptTest {

    private val fixture = """
        {
          "added_tokens": [
            {"id": 0, "content": "<pad>", "special": true},
            {"id": 1, "content": "<eos>", "special": true},
            {"id": 2, "content": "<bos>", "special": true},
            {"id": 3, "content": "<unk>", "special": true},
            {"id": 4, "content": "<mask>", "special": true}
          ],
          "model": {
            "type": "BPE",
            "byte_fallback": true,
            "vocab": {
              "<pad>": 0, "<eos>": 1, "<bos>": 2, "<unk>": 3, "<mask>": 4,
              "\u2581": 10, "a": 11, "b": 12, "\u2581a": 13, "\u2581b": 14, "ab": 15
            },
            "merges": [["a", "b"], ["\u2581", "a"], ["\u2581", "b"]]
          }
        }
    """.trimIndent()

    private fun realTokenizer(): LayaTokenizer? {
        val file = System.getenv("LAYA_TOKENIZER")?.let(::File)?.takeIf { it.isFile }
            ?: File("/tmp/laya-tok.json").takeIf { it.isFile }
            ?: return null
        return LayaTokenizer.fromFiles(file)
    }

    @Test
    fun `structure places class, separators and option markers`() {
        val tokenizer = LayaTokenizer.fromReader(StringReader(fixture))
        val sequence = LayaPrompt.build(tokenizer, state = "ab", instructions = "ab")

        assertEquals(LayaQuestionType.NOUL.qtype, sequence.qtype)
        assertEquals(tokenizer.clsTokenId, sequence.inputIds.first())
        assertEquals(tokenizer.sepTokenId, sequence.inputIds.last())
        assertEquals(2, sequence.markerCount)
        assertEquals(2, sequence.markerMask.count { it })
        sequence.markerPos.forEach { position ->
            assertEquals(tokenizer.maskTokenId, sequence.inputIds[position])
        }
        assertEquals(sequence.length, sequence.attentionMask().size)
    }

    @Test
    fun `matches the reference golden sequences`() {
        val tokenizer = realTokenizer()
        assumeTrue("Set LAYA_TOKENIZER to the mmBERT tokenizer.json to run this test", tokenizer != null)

        val japan = LayaQuestionType.NOUL

        val tokyo = LayaPrompt.build(tokenizer!!, "東京都", "日本のものか？", japan)
        assertArrayEquals(
            intArrayOf(
                2, 552, 6311, 2872, 235292, 26867, 116296, 235443, 235544, 1,
                4, 1566, 235292, 793, 235269, 573, 6218, 1721, 780, 3385,
                4, 1382, 235292, 7778, 235269, 573, 6218, 12723, 1, 201202, 1
            ),
            tokyo.inputIds
        )
        assertArrayEquals(intArrayOf(10, 20), tokyo.markerPos)
        assertTrue(tokyo.markerMask.all { it })

        val saitama = LayaPrompt.build(tokenizer, "埼玉県", "日本のものか？", japan)
        assertArrayEquals(
            intArrayOf(
                2, 552, 6311, 2872, 235292, 26867, 116296, 235443, 235544, 1,
                4, 1566, 235292, 793, 235269, 573, 6218, 1721, 780, 3385,
                4, 1382, 235292, 7778, 235269, 573, 6218, 12723, 1, 235248, 207728, 1
            ),
            saitama.inputIds
        )
        assertArrayEquals(intArrayOf(10, 20), saitama.markerPos)

        val california = LayaPrompt.build(tokenizer, "カリフォルニア州", "Is it in Japan?", japan)
        assertArrayEquals(
            intArrayOf(
                2, 552, 6311, 2872, 235292, 2125, 665, 575, 5928, 235336, 1,
                4, 1566, 235292, 793, 235269, 573, 6218, 1721, 780, 3385,
                4, 1382, 235292, 7778, 235269, 573, 6218, 12723, 1,
                8595, 235505, 142355, 39021, 236598, 1
            ),
            california.inputIds
        )
        assertArrayEquals(intArrayOf(11, 21), california.markerPos)
    }

    @Test
    fun `noul renders the reference false and true options`() {
        assertEquals(
            listOf("false: no, the statement does not hold", "true: yes, the statement holds"),
            LayaPrompt.options(LayaQuestionType.NOUL)
        )
    }
}
