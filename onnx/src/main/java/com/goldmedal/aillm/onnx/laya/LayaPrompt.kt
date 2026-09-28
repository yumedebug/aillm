package com.goldmedal.aillm.onnx.laya

import com.goldmedal.aillm.ai.decision.LayaQuestionType

/**
 * One prepared decision example: the exact tensors the Laya graph consumes.
 *
 * - [inputIds] / [attentionMask] are the padded token sequence.
 * - [markerPos] holds, for every answer option, the index in [inputIds] of that
 *   option's `[MASK]` marker. This is the token whose hidden state the decision
 *   head scores — Laya is not a text-in / class-out classifier.
 * - [markerMask] marks which of those positions are real (a marker pushed past
 *   `max_len` is dropped).
 * - [qtype] is `0` choice, `1` score, `2` noul.
 */
data class LayaSequence(
    val inputIds: IntArray,
    val markerPos: IntArray,
    val markerMask: BooleanArray,
    val qtype: Int
) {
    val length: Int get() = inputIds.size
    val markerCount: Int get() = markerPos.size

    fun attentionMask(): IntArray = IntArray(inputIds.size) { 1 }
}

/**
 * Laya's prompt construction, ported from `laya.common.build_sequence`.
 *
 * ```
 * [CLS] <type> question: <instructions> [SEP]
 * [MASK] option0 [MASK] option1 [SEP] <state> [SEP]
 * ```
 *
 * Each option is prefixed by its own `[MASK]` marker; the decision head reads
 * the hidden state at exactly those positions. The question head and its options
 * share a fixed `head_max_len` budget, and the state fills the remainder up to
 * `max_len`.
 *
 * Nothing here hard-codes a token id: `[CLS]`, `[SEP]` and `[MASK]` come from the
 * tokenizer.
 */
object LayaPrompt {

    /** The most tokens one option may use in the question head. */
    private const val OPTION_MAX_TOKENS = 48

    /** The noul primitive is always a two-option false/true question. */
    private val NOUL_OPTIONS = listOf(
        "false: no, the statement does not hold",
        "true: yes, the statement holds"
    )

    /** The option texts a question renders, in label order. */
    fun options(questionType: LayaQuestionType): List<String> = when (questionType) {
        LayaQuestionType.NOUL -> NOUL_OPTIONS
        else -> throw IllegalArgumentException("${questionType.key} questions are not supported in this build")
    }

    /**
     * Builds the sequence for one (state, question) pair.
     *
     * [state] and [instructions] are the raw user text; the mask token is
     * stripped from both, exactly as the reference does before tokenizing.
     */
    fun build(
        tokenizer: LayaTokenizer,
        state: String,
        instructions: String,
        questionType: LayaQuestionType = LayaQuestionType.NOUL,
        config: LayaConfig = LayaConfig()
    ): LayaSequence {
        val maskToken = tokenizer.maskTokenId
        val options = options(questionType)

        val headText = "${questionType.key} question: ${stripMask(instructions, tokenizer)}"
        val headIds = tokenizer.encode(headText).toMutableList()

        // Each option is [MASK] + its tokenized text, capped so a long option
        // cannot crowd out the rest of the head.
        val optionIds = options.map { option ->
            val tokens = tokenizer.encode(" " + stripMask(option, tokenizer)).take(OPTION_MAX_TOKENS)
            IntArray(tokens.size + 1) { index -> if (index == 0) maskToken else tokens[index - 1] }
        }

        var budget = config.headMaxLen - optionIds.sumOf { it.size }
        var finalOptions = optionIds
        if (budget < 16) {
            val per = maxOf(4, (config.headMaxLen - 16) / maxOf(1, optionIds.size))
            finalOptions = optionIds.map { it.take(per).toIntArray() }
            budget = config.headMaxLen - finalOptions.sumOf { it.size }
        }

        if (headIds.size > maxOf(8, budget)) {
            while (headIds.size > maxOf(8, budget)) headIds.removeAt(headIds.size - 1)
        }

        val ids = ArrayList<Int>(config.maxLen)
        ids.add(tokenizer.clsTokenId)
        ids.addAll(headIds)
        ids.add(tokenizer.sepTokenId)

        val markers = ArrayList<Int>(finalOptions.size)
        for (option in finalOptions) {
            markers.add(ids.size)
            for (token in option) ids.add(token)
        }
        ids.add(tokenizer.sepTokenId)

        val room = maxOf(0, config.maxLen - ids.size - 1)
        val stateTokens = tokenizer.encode(stripMask(state, tokenizer))
        for (index in 0 until minOf(room, stateTokens.size)) ids.add(stateTokens[index])
        ids.add(tokenizer.sepTokenId)

        val truncated = if (ids.size > config.maxLen) ids.subList(0, config.maxLen) else ids

        val markerPos = ArrayList<Int>(markers.size)
        val markerMask = ArrayList<Boolean>(markers.size)
        for (marker in markers) {
            markerPos.add(marker)
            markerMask.add(marker < config.maxLen)
        }

        return LayaSequence(
            inputIds = truncated.toIntArray(),
            markerPos = markerPos.toIntArray(),
            markerMask = markerMask.toBooleanArray(),
            qtype = questionType.qtype
        )
    }

    /** Laya removes the mask token from user text so it cannot create stray markers. */
    private fun stripMask(text: String, tokenizer: LayaTokenizer): String =
        if (tokenizer.maskTokenText.isEmpty()) text else text.replace(tokenizer.maskTokenText, " ")
}
