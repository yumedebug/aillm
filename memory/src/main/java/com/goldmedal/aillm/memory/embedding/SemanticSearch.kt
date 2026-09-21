package com.goldmedal.aillm.memory.embedding

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.core.database.UserMemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrieves the memories that should be put in front of the model.
 *
 * Embeddings are used when an embedding backend is actually loaded. Until one
 * exists, retrieval is lexical: the user's message is turned into search terms
 * and memories are ranked by how many of them they contain. Scripts without
 * word separators (CJK) also contribute character n-grams, because a whole
 * Japanese sentence would otherwise collapse into one unmatchable term.
 *
 * A few of the most important memories are always included, so the assistant
 * keeps knowing who it is talking to even when the current message happens to
 * share no words with them.
 */
@Singleton
class SemanticSearch @Inject constructor(
    private val userMemoryDao: UserMemoryDao,
    private val embeddingModel: EmbeddingModel
) {

    suspend fun search(query: String, limit: Int = 10): List<UserMemoryEntity> {
        val all = runCatching { userMemoryDao.getAllActiveMemoriesOnce() }
            .getOrDefault(emptyList())
        if (all.isEmpty()) return emptyList()

        val standing = all.asSequence()
            .filter { it.importance > 0 }
            .sortedWith(
                compareByDescending<UserMemoryEntity> { it.importance }
                    .thenByDescending { it.updatedAt }
            )
            .take(STANDING_MEMORIES)
            .toList()

        val lexical = lexicalRanked(query, all, limit)
        val similar = if (embeddingModel.isLoaded) embeddedRanked(query, all, limit) else emptyList()

        return (lexical + similar + standing)
            .distinctBy { it.id }
            .take(limit)
    }

    /**
     * Memories ranked by how many of the message's terms they contain. Long
     * words count for more than n-grams, and a memory has to clear
     * [MIN_SCORE] to be considered at all — otherwise a couple of shared
     * Japanese characters would pull in unrelated memories.
     */
    private fun lexicalRanked(
        query: String,
        all: List<UserMemoryEntity>,
        limit: Int
    ): List<UserMemoryEntity> {
        val terms = searchTerms(query)
        if (terms.words.isEmpty() && terms.grams.isEmpty()) return emptyList()

        return all.map { memory ->
            memory to score(memory, terms)
        }.filter { it.second >= MIN_SCORE }
            .sortedWith(
                compareByDescending<Pair<UserMemoryEntity, Int>> { it.second }
                    .thenByDescending { it.first.importance }
                    .thenByDescending { it.first.updatedAt }
            )
            .take(limit)
            .map { it.first }
    }

    private suspend fun embeddedRanked(
        query: String,
        all: List<UserMemoryEntity>,
        limit: Int
    ): List<UserMemoryEntity> = runCatching {
        val queryEmbedding = embeddingModel.embed(query).getOrThrow()
        all.mapNotNull { memory ->
            val embedding = embeddingModel.embed(memory.searchText()).getOrNull()
                ?: return@mapNotNull null
            val similarity = embeddingModel.cosineSimilarity(queryEmbedding, embedding)
            if (similarity < SIMILARITY_FLOOR) null else memory to similarity
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }.getOrDefault(emptyList())

    private fun score(memory: UserMemoryEntity, terms: SearchTerms): Int {
        val haystack = memory.searchText().lowercase()
        val wordHits = terms.words.count { haystack.contains(it) }
        val gramHits = terms.grams.count { haystack.contains(it) }
        return wordHits * WORD_WEIGHT + gramHits
    }

    /** Splits a message into whole-word terms plus n-grams for CJK runs. */
    internal fun searchTerms(text: String): SearchTerms {
        val words = mutableSetOf<String>()
        val grams = mutableSetOf<String>()

        text.lowercase().split(TERM_SEPARATOR).forEach { token ->
            if (token.length >= MIN_WORD_LENGTH) words.add(token)
            if (token.length >= MIN_NGRAM_WORD_LENGTH && CJK.containsMatchIn(token)) {
                for (size in NGRAM_MIN..NGRAM_MAX) {
                    if (token.length < size) continue
                    for (start in 0..token.length - size) grams.add(token.substring(start, start + size))
                }
            }
        }
        return SearchTerms(words = words, grams = grams)
    }

    private fun UserMemoryEntity.searchText(): String = "$category $key $value"

    /** Words and character n-grams extracted from one message. */
    internal data class SearchTerms(
        val words: Set<String> = emptySet(),
        val grams: Set<String> = emptySet()
    )

    private companion object {
        private const val STANDING_MEMORIES = 4
        private const val MIN_WORD_LENGTH = 3
        private const val MIN_NGRAM_WORD_LENGTH = 2
        private const val NGRAM_MIN = 2
        private const val NGRAM_MAX = 3
        private const val WORD_WEIGHT = 3
        private const val MIN_SCORE = 3
        private const val SIMILARITY_FLOOR = 0.3f

        private val TERM_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

        /** Hiragana, katakana, CJK, halfwidth katakana, Hangul. */
        private val CJK = Regex("[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uff66-\\uff9f\\uac00-\\ud7af]")
    }
}
