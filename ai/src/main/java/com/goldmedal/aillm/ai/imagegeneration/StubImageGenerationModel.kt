package com.goldmedal.aillm.ai.imagegeneration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.delay

class StubImageGenerationModel : ImageGenerationModel {
    override val name: String = "Stub Image Generation Model"
    override val isLoaded: Boolean = true

    override suspend fun load(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun unload(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun generateImage(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        guidanceScale: Float
    ): Result<Bitmap> {
        delay(2000)

        // Create a stub bitmap with text
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = 24f
            isAntiAlias = true
        }

        // Draw background
        canvas.drawColor(Color.BLACK)

        // Draw text
        val text = "Stub Image: $prompt"
        val maxWidth = width - 40f
        val words = text.split(" ")
        var line = ""
        var y = 50f

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth) {
                canvas.drawText(line, 20f, y, paint)
                line = word
                y += 30f
            } else {
                line = testLine
            }
        }
        canvas.drawText(line, 20f, y, paint)

        // Draw prompt at bottom
        paint.textSize = 16f
        paint.color = Color.GRAY
        canvas.drawText("In production, this would be a real AI-generated image", 20f, height - 20f, paint)

        return Result.success(bitmap)
    }
}
