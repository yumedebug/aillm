package com.goldmedal.aillm.onnx.laya

import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets

/**
 * Laya's tokenizer, implemented from `tokenizer.json` in pure Kotlin.
 *
 * Laya is multilingual and its checkpoint ships the mmBERT tokenizer: a
 * SentencePiece-style BPE with a 256k vocabulary, byte fallback, and a
 * `Metaspace` pre-tokenizer (spaces become `▁`). That file is ~34 MB, so it is
 * read once, with [JsonStream], which never builds a JSON tree.
 *
 * Only what Laya's prompt construction needs is implemented:
 *
 * - the `Replace(" " -> "▁")` normalizer and the `Metaspace` pre-tokenizer,
 * - BPE merges by rank over single code points,
 * - byte fallback (`<0xXX>`) for code points outside the vocabulary.
 *
 * Nothing about the vocabulary is hard-coded: the special token ids (`<bos>`,
 * `<eos>`, `<mask>`, `<unk>`) are resolved from the names in
 * `tokenizer_config.json` and the ids in `tokenizer.json`.
 */
class LayaTokenizer private constructor(
    private val vocab: HashMap<String, Int>,
    /** pairKey(idA, idB) -> ((resultId << 32) or mergeRank). */
    private val merges: HashMap<Long, Long>,
    private val byteTokenIds: IntArray,
    val clsTokenId: Int,
    val sepTokenId: Int,
    val maskTokenId: Int,
    val unkTokenId: Int,
    val padTokenId: Int,
    /** The textual form of the mask token (`<mask>`), used to strip stray markers. */
    val maskTokenText: String
) : Closeable {

    /** The vocabulary size, for diagnostics. */
    val vocabularySize: Int get() = vocab.size

    /** How many BPE merges were loaded, for diagnostics. */
    val mergeCount: Int get() = merges.size

    /**
     * Encodes [text] the way `laya.common.encode_text(..., add_special_tokens=False)`
     * does: normalise, pre-tokenize, BPE each piece. No `[CLS]`/`[SEP]` are added
     * — Laya places those itself when it builds the decision sequence.
     */
    fun encode(text: String): IntArray {
        if (text.isEmpty()) return IntArray(0)
        val normalised = text.replace(' ', METASPACE)
        val out = ArrayList<Int>(normalised.length)
        for (piece in metaspaceSplit(normalised)) {
            appendBpe(piece, out)
        }
        return out.toIntArray()
    }

    override fun close() {
        vocab.clear()
        merges.clear()
    }

    // ------------------------------------------------------------------ BPE

    /** Applies BPE to one pre-tokenized piece, appending ids to [out]. */
    private fun appendBpe(piece: String, out: MutableList<Int>) {
        if (piece.isEmpty()) return

        // Initial symbols: one per Unicode code point, expanded to its UTF-8
        // byte tokens when the code point is not in the vocabulary.
        val symbols = ArrayList<Int>(piece.length)
        var i = 0
        while (i < piece.length) {
            val codePoint = Character.codePointAt(piece, i)
            val length = Character.charCount(codePoint)
            val symbol = piece.substring(i, i + length)
            val id = vocab[symbol]
            if (id != null) {
                symbols.add(id)
            } else {
                appendByteFallback(symbol, symbols)
                i += length
                continue
            }
            i += length
        }

        // Merge the adjacent pair with the highest priority (lowest rank) until
        // no mergeable pair remains. Pieces are word-sized, so the simple scan
        // is cheaper than maintaining a merge queue.
        while (symbols.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1
            var bestPacked = 0L
            for (index in 0 until symbols.size - 1) {
                val packed = merges[pairKey(symbols[index], symbols[index + 1])] ?: continue
                val rank = (packed and RANK_MASK).toInt()
                if (rank < bestRank) {
                    bestRank = rank
                    bestIndex = index
                    bestPacked = packed
                }
            }
            if (bestIndex < 0) break
            symbols[bestIndex] = (bestPacked ushr 32).toInt()
            symbols.removeAt(bestIndex + 1)
        }

        // fuse_unk: consecutive unknown symbols collapse into one.
        var previous = -1
        for (symbol in symbols) {
            if (symbol == unkTokenId && previous == unkTokenId) continue
            out.add(symbol)
            previous = symbol
        }
    }

    /**
     * Byte fallback: a code point outside the vocabulary is emitted as one
     * `<0xXX>` token per UTF-8 byte. When a byte token is itself missing the
     * unknown token is used.
     */
    private fun appendByteFallback(symbol: String, symbols: MutableList<Int>) {
        for (byte in symbol.toByteArray(StandardCharsets.UTF_8)) {
            symbols.add(byteTokenIds[byte.toInt() and 0xFF])
        }
    }

    private fun pairKey(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

    /**
     * The `Metaspace` pre-tokenizer: with `prepend_scheme = "always"`, a `▁` is
     * prepended when the text does not already start with one, and the text is
     * then split before every `▁`.
     */
    private fun metaspaceSplit(text: String): List<String> {
        val value = if (text.startsWith(METASPACE)) text else METASPACE + text
        val pieces = ArrayList<String>()
        var start = 0
        for (index in 1 until value.length) {
            if (value[index] == METASPACE) {
                pieces.add(value.substring(start, index))
                start = index
            }
        }
        pieces.add(value.substring(start))
        return pieces
    }

    // ------------------------------------------------------------------ loading

    companion object {
        private const val METASPACE = '\u2581'
        private const val RANK_MASK = 0xFFFF_FFFFL

        /** Default special-token names if `tokenizer_config.json` is unavailable. */
        private const val DEFAULT_CLS = "<bos>"
        private const val DEFAULT_SEP = "<eos>"
        private const val DEFAULT_MASK = "<mask>"
        private const val DEFAULT_UNK = "<unk>"
        private const val DEFAULT_PAD = "<pad>"

        /** Loads `tokenizer.json`, resolving special tokens from [tokenizerConfigFile] when given. */
        fun fromFiles(tokenizerFile: File, tokenizerConfigFile: File? = null): LayaTokenizer {
            if (!tokenizerFile.isFile) throw FileNotFoundException("tokenizer.json not found: $tokenizerFile")
            val names = tokenizerConfigFile?.takeIf { it.isFile }?.let(::readSpecialTokenNames)
                ?: SpecialTokenNames(DEFAULT_CLS, DEFAULT_SEP, DEFAULT_MASK, DEFAULT_UNK, DEFAULT_PAD)
            return tokenizerFile.bufferedReader(StandardCharsets.UTF_8).use { fromReader(it, names) }
        }

        /** Parses a `tokenizer.json` document from [reader]. */
        fun fromReader(reader: Reader): LayaTokenizer = fromReader(
            reader,
            SpecialTokenNames(DEFAULT_CLS, DEFAULT_SEP, DEFAULT_MASK, DEFAULT_UNK, DEFAULT_PAD)
        )

        private fun fromReader(reader: Reader, names: SpecialTokenNames): LayaTokenizer {
            val vocab = HashMap<String, Int>(300_000)
            val merges = HashMap<Long, Long>(600_000)
            val addedTokens = HashMap<String, Int>(512)
            var modelSeen = false

            JsonStream(BufferedReader(reader, 1 shl 16)).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "added_tokens" -> readAddedTokens(json, addedTokens, vocab)
                        "model" -> {
                            modelSeen = true
                            readModel(json, vocab, merges)
                        }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            check(modelSeen) { "tokenizer.json is missing its \"model\" section" }

            fun idOf(name: String, fallback: Int): Int =
                addedTokens[name] ?: vocab[name] ?: fallback

            val byteTokenIds = IntArray(256) { byte ->
                val token = "<0x%02X>".format(byte)
                vocab[token] ?: addedTokens[token] ?: 0
            }

            val unk = idOf(names.unk, 0)
            return LayaTokenizer(
                vocab = vocab,
                merges = merges,
                byteTokenIds = IntArray(256) { if (byteTokenIds[it] == 0) unk else byteTokenIds[it] },
                clsTokenId = idOf(names.cls, 2),
                sepTokenId = idOf(names.sep, 1),
                maskTokenId = idOf(names.mask, 4),
                unkTokenId = unk,
                padTokenId = idOf(names.pad, 0),
                maskTokenText = names.mask
            )
        }

        private fun readAddedTokens(
            json: JsonStream,
            addedTokens: HashMap<String, Int>,
            vocab: HashMap<String, Int>
        ) {
            json.beginArray()
            while (json.hasNext()) {
                var id = -1
                var content: String? = null
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "id" -> id = json.nextInt()
                        "content" -> content = json.nextString()
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                if (content != null && id >= 0) {
                    addedTokens[content] = id
                    vocab[content] = id
                }
            }
            json.endArray()
        }

        private fun readModel(
            json: JsonStream,
            vocab: HashMap<String, Int>,
            merges: HashMap<Long, Long>
        ) {
            // Merges refer to vocabulary entries, so the vocabulary must be read
            // first. In the shipped tokenizer.json it is declared before merges.
            val pendingMerges = ArrayList<Pair<String, String>>()
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "vocab" -> readVocab(json, vocab)
                    "merges" -> readMerges(json, vocab, merges, pendingMerges)
                    else -> json.skipValue()
                }
            }
            json.endObject()
            val leftover = pendingMerges.isNotEmpty()
            if (leftover) {
                for ((a, b) in pendingMerges) addMerge(a, b, vocab, merges)
            }
        }

        private fun readVocab(json: JsonStream, vocab: HashMap<String, Int>) {
            json.beginObject()
            while (json.hasNext()) {
                val token = json.nextName()
                vocab[token] = json.nextInt()
            }
            json.endObject()
        }

        private fun readMerges(
            json: JsonStream,
            vocab: HashMap<String, Int>,
            merges: HashMap<Long, Long>,
            pending: MutableList<Pair<String, String>>
        ) {
            json.beginArray()
            while (json.hasNext()) {
                when (json.peek()) {
                    JsonStream.Type.BEGIN_ARRAY -> {
                        json.beginArray()
                        val a = json.nextString()
                        val b = json.nextString()
                        // Tolerate any extra fields in a merge entry.
                        while (json.hasNext()) json.skipValue()
                        json.endArray()
                        if (vocab.isEmpty()) pending.add(a to b) else addMerge(a, b, vocab, merges)
                    }
                    JsonStream.Type.STRING -> {
                        // Older format: "a b".
                        val entry = json.nextString()
                        val split = entry.indexOf(' ')
                        if (split <= 0) continue
                        val a = entry.substring(0, split)
                        val b = entry.substring(split + 1)
                        if (vocab.isEmpty()) pending.add(a to b) else addMerge(a, b, vocab, merges)
                    }
                    else -> json.skipValue()
                }
            }
            json.endArray()
        }

        private fun addMerge(
            a: String,
            b: String,
            vocab: HashMap<String, Int>,
            merges: HashMap<Long, Long>
        ) {
            val idA = vocab[a] ?: return
            val idB = vocab[b] ?: return
            val result = vocab[a + b] ?: return
            val key = (idA.toLong() shl 32) or (idB.toLong() and 0xFFFFFFFFL)
            merges[key] = (result.toLong() shl 32) or merges.size.toLong()
        }

        /** The special-token names declared by `tokenizer_config.json`. */
        fun readSpecialTokenNames(file: File): SpecialTokenNames =
            file.bufferedReader(StandardCharsets.UTF_8).use { readSpecialTokenNames(it) }

        fun readSpecialTokenNames(reader: Reader): SpecialTokenNames {
            var cls: String? = null
            var sep: String? = null
            var bos: String? = null
            var eos: String? = null
            var mask: String? = null
            var unk: String? = null
            var pad: String? = null
            JsonStream(BufferedReader(reader)).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "cls_token" -> cls = json.nextString()
                        "sep_token" -> sep = json.nextString()
                        "bos_token" -> bos = json.nextString()
                        "eos_token" -> eos = json.nextString()
                        "mask_token" -> mask = json.nextString()
                        "unk_token" -> unk = json.nextString()
                        "pad_token" -> pad = json.nextString()
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            return SpecialTokenNames(
                cls = cls ?: bos ?: DEFAULT_CLS,
                sep = sep ?: eos ?: DEFAULT_SEP,
                mask = mask ?: DEFAULT_MASK,
                unk = unk ?: DEFAULT_UNK,
                pad = pad ?: DEFAULT_PAD
            )
        }

        /** Loads a tokenizer.json from the module's resources (used by tests). */
        internal fun fromStream(stream: java.io.InputStream): LayaTokenizer =
            fromReader(InputStreamReader(stream, StandardCharsets.UTF_8))
    }

    /** The four special tokens Laya's sequence builder needs, by name. */
    data class SpecialTokenNames(
        val cls: String,
        val sep: String,
        val mask: String,
        val unk: String,
        val pad: String
    )
}
