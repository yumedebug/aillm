package com.goldmedal.aillm.memory.consolidation

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.core.database.UserMemoryEntity
import com.goldmedal.aillm.memory.embedding.SemanticSearch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps memory tidy as it grows across conversations.
 *
 * Two things go wrong when extracted facts are simply written down: the same
 * fact is stored again every time it is mentioned, and a category slowly fills
 * with statements that describe a past state of the user's life. So a fact is
 * never just inserted — it is merged into what is already known (being said
 * again raises confidence instead of adding a near-copy), and each category is
 * capped at its few most recent statements, with older ones **archived rather
 * than deleted** so the user can always bring them back from Settings → Memory.
 */
@Singleton
class MemoryConsolidator @Inject constructor(
    private val userMemoryDao: UserMemoryDao,
    private val semanticSearch: SemanticSearch
) {

    /**
     * Writes a fact, merging it into existing knowledge. Returns the row id.
     */
    suspend fun store(
        category: String,
        key: String,
        value: String,
        importance: Int,
        confidence: Float,
        sourceMessageId: Long?
    ): Long {
        val now = System.currentTimeMillis()

        // Same slot: the new statement is what the user says now.
        userMemoryDao.getMemoryByKey(category, key)?.let { existing ->
            userMemoryDao.updateMemory(
                existing.copy(
                    value = value,
                    importance = maxOf(existing.importance, importance),
                    confidence = reinforced(existing.confidence, confidence),
                    updatedAt = now,
                    sourceMessageId = sourceMessageId ?: existing.sourceMessageId,
                    isActive = true
                )
            )
            enforceCategoryLimit(category)
            return existing.id
        }

        // Same fact, different wording: reinforce instead of duplicating.
        findNearDuplicate(category, value)?.let { duplicate ->
            userMemoryDao.updateMemory(
                duplicate.copy(
                    key = key,
                    value = value,
                    importance = maxOf(duplicate.importance, importance),
                    confidence = reinforced(duplicate.confidence, confidence),
                    updatedAt = now,
                    sourceMessageId = sourceMessageId ?: duplicate.sourceMessageId,
                    isActive = true
                )
            )
            enforceCategoryLimit(category)
            return duplicate.id
        }

        val id = userMemoryDao.insertMemory(
            UserMemoryEntity(
                category = category,
                key = key,
                value = value,
                importance = importance,
                confidence = confidence,
                sourceMessageId = sourceMessageId
            )
        )
        enforceCategoryLimit(category)
        return id
    }

    /**
     * Sweeps memories that piled up before (or in spite of) write-time merging:
     * collapses near-duplicates inside each category and applies the caps.
     * Returns how many rows were archived. Pass a category to sweep just one.
     */
    suspend fun cleanUp(category: String? = null): Int {
        val categories = category?.let { listOf(it) }
            ?: userMemoryDao.getAllActiveMemoriesOnce().map { it.category }.distinct()

        var archived = 0
        for (name in categories) {
            archived += mergeDuplicates(name)
            archived += enforceCategoryLimit(name)
        }
        return archived
    }

    suspend fun restore(memoryId: Long) {
        val memory = userMemoryDao.getMemoryById(memoryId) ?: return
        userMemoryDao.setMemoryActive(memoryId, true)
        // Restoring should not push something else out again immediately.
        enforceCategoryLimit(memory.category, limit = limitFor(memory.category) + 1)
    }

    /** Newest statement wins; older near-duplicates are folded into it. */
    private suspend fun mergeDuplicates(category: String): Int {
        val rows = userMemoryDao.getActiveMemoriesByCategoryOnce(category)
            .sortedByDescending { it.updatedAt }
        if (rows.size < 2) return 0

        val kept = mutableListOf<UserMemoryEntity>()
        var archived = 0

        for (row in rows) {
            val rowTerms = semanticSearch.searchTerms(row.value)
            val index = kept.indexOfFirst {
                similarity(rowTerms, semanticSearch.searchTerms(it.value)) >= DUPLICATE_SIMILARITY
            }
            if (index < 0) {
                kept.add(row)
                continue
            }
            val merged = kept[index].copy(
                importance = maxOf(kept[index].importance, row.importance),
                confidence = reinforced(kept[index].confidence, row.confidence),
                updatedAt = maxOf(kept[index].updatedAt, row.updatedAt)
            )
            userMemoryDao.updateMemory(merged)
            kept[index] = merged
            userMemoryDao.setMemoryActive(row.id, false)
            archived++
        }
        return archived
    }

    /**
     * Keeps the most recently stated entries in a category and archives the
     * rest. Old statements are usually still true, but the recent ones are the
     * ones that describe the user now.
     */
    private suspend fun enforceCategoryLimit(category: String, limit: Int = limitFor(category)): Int {
        val active = userMemoryDao.getActiveMemoriesByCategoryOnce(category)
        val excess = active.drop(limit)
        excess.forEach { userMemoryDao.setMemoryActive(it.id, false) }
        return excess.size
    }

    private suspend fun findNearDuplicate(category: String, value: String): UserMemoryEntity? {
        val terms = semanticSearch.searchTerms(value)
        if (terms.words.isEmpty() && terms.grams.isEmpty()) return null

        return userMemoryDao.getActiveMemoriesByCategoryOnce(category)
            .map { it to similarity(terms, semanticSearch.searchTerms(it.value)) }
            .filter { it.second >= DUPLICATE_SIMILARITY }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * How much of the shorter statement is contained in the longer one. Word
     * hits and character n-grams are treated alike, which is what makes this
     * work for Japanese as well as for English.
     */
    private fun similarity(a: SemanticSearch.SearchTerms, b: SemanticSearch.SearchTerms): Float {
        val left = a.words + a.grams
        val right = b.words + b.grams
        if (left.isEmpty() || right.isEmpty()) return 0f
        val overlap = left.count { it in right }
        return overlap.toFloat() / minOf(left.size, right.size)
    }

    /** Saying the same thing again should mean something. */
    private fun reinforced(existing: Float, incoming: Float): Float =
        (maxOf(existing, incoming) + REINFORCEMENT_STEP).coerceAtMost(1f)

    private fun limitFor(category: String): Int = when (category.lowercase()) {
        "operating system", "device", "profile" -> 3
        "programming language" -> 4
        "preference" -> 8
        else -> 5
    }

    private companion object {
        private const val DUPLICATE_SIMILARITY = 0.6f
        private const val REINFORCEMENT_STEP = 0.05f
    }
}
