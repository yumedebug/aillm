package com.goldmedal.aillm.core.constants

object Constants {
    // Database
    const val DATABASE_NAME = "aillm_database"

    // Memory categories
    object MemoryCategory {
        const val PROFILE = "PROFILE"
        const val PREFERENCE = "PREFERENCE"
        const val PROJECT = "PROJECT"
        const val EVENT = "EVENT"
        const val CONVERSATION = "CONVERSATION"
        const val IMAGE = "IMAGE"
        const val FILE = "FILE"
        const val TECHNICAL = "TECHNICAL"
    }

    // Message roles
    object MessageRole {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val SYSTEM = "system"
    }

    // Model types
    object ModelType {
        const val CHAT = "CHAT"
        const val VISION = "VISION"
        const val IMAGE_GENERATION = "IMAGE_GENERATION"
    }

    // Default settings
    object Defaults {
        const val MAX_RECENT_MESSAGES = 20
        const val MAX_RELEVANT_MEMORIES = 10
        const val MAX_CONTEXT_LENGTH = 4096
        const val TEMPERATURE = 0.7f
        const val MAX_TOKENS = 2048
    }

    // File paths
    object Paths {
        const val USER_FILES_DIR = "user_files"
        const val IMAGES_DIR = "images"
        const val MODELS_DIR = "models"
    }
}
