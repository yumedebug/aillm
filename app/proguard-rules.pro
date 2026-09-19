# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.goldmedal.aillm.**$$serializer { *; }
-keepclassmembers class com.goldmedal.aillm.** {
    *** Companion;
}
-keepclasseswithmembers class com.goldmedal.aillm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep class com.goldmedal.aillm.core.database.** { *; }
-keep class com.goldmedal.aillm.ai.chat.ChatMessage { *; }
-keep class com.goldmedal.aillm.ai.vision.ImageAnalysis { *; }
-keep class com.goldmedal.aillm.ai.modelmanager.ModelInfo { *; }
-keep class com.goldmedal.aillm.search.SearchResult { *; }
-keep class com.goldmedal.aillm.search.SearchItem { *; }
-keep class com.goldmedal.aillm.search.TavilyRequest { *; }
-keep class com.goldmedal.aillm.search.TavilyResponse { *; }
-keep class com.goldmedal.aillm.search.TavilyResult { *; }
