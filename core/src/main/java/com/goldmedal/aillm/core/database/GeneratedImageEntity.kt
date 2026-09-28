package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One picture produced by the image screen.
 *
 * The pixels live in the app's own `generated` directory, next to the models —
 * nothing is published to the gallery unless the user asks for it. This row is
 * only what makes the picture findable again: how it was asked for, which model
 * drew it, and when.
 */
@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** File name inside the app's generated-images directory. */
    val fileName: String,

    /** The prompt exactly as it was entered. */
    val prompt: String,

    val negativePrompt: String = "",

    /** Catalogue id of the model that produced it. */
    val modelId: String = "",

    val modelName: String = "",

    val width: Int = 0,

    val height: Int = 0,

    val steps: Int = 0,

    val createdAt: Long = System.currentTimeMillis()
)
