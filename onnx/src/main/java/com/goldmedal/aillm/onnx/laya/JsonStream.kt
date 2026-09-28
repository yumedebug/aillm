package com.goldmedal.aillm.onnx.laya

import java.io.Closeable
import java.io.Reader

/**
 * A minimal push-style JSON reader.
 *
 * `tokenizer.json` for mmBERT is ~34 MB: 256k vocabulary entries and ~580k BPE
 * merges. Parsing it into a `JSONObject` tree would allocate a second copy of
 * the whole document in the heap, so the tokenizer is loaded with this
 * single-pass pull reader instead, which never materialises the tree.
 *
 * It is deliberately small and only supports what a JSON document requires:
 * objects, arrays, strings (with escapes), numbers, booleans and null.
 */
internal class JsonStream(private val reader: Reader) : Closeable {

    enum class Type {
        BEGIN_OBJECT,
        END_OBJECT,
        BEGIN_ARRAY,
        END_ARRAY,
        NAME,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,
        END_DOCUMENT
    }

    private var peeked: Type? = null
    private var pendingName: String? = null
    private var pendingString: String? = null
    private var pendingNumber: String? = null
    private var pendingBoolean: Boolean = false

    /** The type of the next token without consuming it. */
    fun peek(): Type = peeked ?: readToken().also { peeked = it }

    fun beginObject() = expect(Type.BEGIN_OBJECT)

    fun endObject() = expect(Type.END_OBJECT)

    fun beginArray() = expect(Type.BEGIN_ARRAY)

    fun endArray() = expect(Type.END_ARRAY)

    /** True while the current array/object still has another element. */
    fun hasNext(): Boolean {
        val type = peek()
        return type != Type.END_ARRAY && type != Type.END_OBJECT
    }

    fun nextName(): String {
        expect(Type.NAME)
        val name = pendingName ?: error("missing object name")
        pendingName = null
        return name
    }

    fun nextString(): String {
        expect(Type.STRING)
        val value = pendingString ?: error("missing string value")
        pendingString = null
        return value
    }

    fun nextInt(): Int {
        val raw = nextNumberString()
        return raw.toIntOrNull()
            ?: raw.toDoubleOrNull()?.toInt()
            ?: error("not an int: $raw")
    }

    fun nextLong(): Long {
        val raw = nextNumberString()
        return raw.toLongOrNull()
            ?: raw.toDoubleOrNull()?.toLong()
            ?: error("not a long: $raw")
    }

    /** The raw text of the next number token (so callers can parse Float/Double). */
    fun nextNumberString(): String {
        expect(Type.NUMBER)
        val raw = pendingNumber ?: error("missing number value")
        pendingNumber = null
        return raw
    }

    fun nextBoolean(): Boolean {
        expect(Type.BOOLEAN)
        return pendingBoolean
    }

    fun skipValue() {
        when (peek()) {
            Type.BEGIN_OBJECT -> {
                beginObject()
                while (hasNext()) {
                    nextName()
                    skipValue()
                }
                endObject()
            }
            Type.BEGIN_ARRAY -> {
                beginArray()
                while (hasNext()) skipValue()
                endArray()
            }
            Type.STRING -> nextString()
            Type.NUMBER -> nextNumberString()
            Type.BOOLEAN -> nextBoolean()
            Type.NULL -> consume(Type.NULL)
            else -> error("cannot skip ${peek()}")
        }
    }

    override fun close() {
        reader.close()
    }

    // ------------------------------------------------------------------ internals

    private fun expect(type: Type) {
        val actual = peek()
        if (actual != type) error("expected $type but found $actual")
        consume(type)
    }

    private fun consume(type: Type) {
        peeked = null
        // The scalar payload (name/string/number) is cleared by its accessor;
        // composite and structural tokens carry no payload.
        when (type) {
            Type.NAME, Type.STRING, Type.NUMBER, Type.BOOLEAN, Type.NULL -> Unit
            else -> Unit
        }
    }

    /** Reads one structural token into the pending slots. */
    private fun readToken(): Type {
        while (true) {
            val c = nextSignificant()
            when {
                c == -1 -> return Type.END_DOCUMENT
                c == '{'.code -> return Type.BEGIN_OBJECT
                c == '}'.code -> return Type.END_OBJECT
                c == '['.code -> return Type.BEGIN_ARRAY
                c == ']'.code -> return Type.END_ARRAY
                c == ','.code || c == ':'.code -> Unit
                c == '"'.code -> {
                    val value = readStringBody()
                    val after = nextSignificant()
                    if (after == ':'.code) {
                        pendingName = value
                        return Type.NAME
                    }
                    unread(after)
                    pendingString = value
                    return Type.STRING
                }
                c == 't'.code -> {
                    expectLiteral("rue")
                    pendingBoolean = true
                    return Type.BOOLEAN
                }
                c == 'f'.code -> {
                    expectLiteral("alse")
                    pendingBoolean = false
                    return Type.BOOLEAN
                }
                c == 'n'.code -> {
                    expectLiteral("ull")
                    return Type.NULL
                }
                else -> {
                    pendingNumber = readNumber(c)
                    return Type.NUMBER
                }
            }
        }
    }

    private var pushback = NONE

    private fun nextSignificant(): Int {
        var c = readChar()
        while (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) {
            c = readChar()
        }
        return c
    }

    private fun readChar(): Int {
        if (pushback != NONE) {
            val c = pushback
            pushback = NONE
            return c
        }
        return reader.read()
    }

    private fun unread(c: Int) {
        pushback = c
    }

    private fun expectLiteral(rest: String) {
        for (expected in rest) {
            val c = readChar()
            if (c != expected.code) error("invalid literal near '$expected'")
        }
    }

    private fun readStringBody(): String {
        val sb = StringBuilder()
        while (true) {
            val c = readChar()
            when {
                c == -1 -> error("unterminated string")
                c == '"'.code -> return sb.toString()
                c == '\\'.code -> {
                    when (val e = readChar()) {
                        '"'.code -> sb.append('"')
                        '\\'.code -> sb.append('\\')
                        '/'.code -> sb.append('/')
                        'b'.code -> sb.append('\b')
                        'f'.code -> sb.append('\u000C')
                        'n'.code -> sb.append('\n')
                        'r'.code -> sb.append('\r')
                        't'.code -> sb.append('\t')
                        'u'.code -> sb.append(readHex4())
                        else -> error("invalid escape \\${e.toChar()}")
                    }
                }
                else -> sb.append(c.toChar())
            }
        }
    }

    private fun readHex4(): Char {
        var value = 0
        repeat(4) {
            val c = readChar()
            val digit = when (c) {
                in '0'.code..'9'.code -> c - '0'.code
                in 'a'.code..'f'.code -> c - 'a'.code + 10
                in 'A'.code..'F'.code -> c - 'A'.code + 10
                else -> error("invalid hex digit in \\u escape")
            }
            value = (value shl 4) or digit
        }
        return value.toChar()
    }

    private fun readNumber(first: Int): String {
        val sb = StringBuilder()
        sb.append(first.toChar())
        while (true) {
            val c = readChar()
            val isNumberChar = c == '-'.code || c == '+'.code || c == '.'.code ||
                c == 'e'.code || c == 'E'.code ||
                (c in '0'.code..'9'.code)
            if (!isNumberChar) {
                unread(c)
                return sb.toString()
            }
            sb.append(c.toChar())
        }
    }

    private companion object {
        const val NONE = -2
    }
}
