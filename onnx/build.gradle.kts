plugins {
    id("com.android.library")
}

android {
    namespace = "com.goldmedal.aillm.onnx"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime ships 32-bit and 64-bit natives. The rest of the app is
        // 64-bit only (see :llm), so the extra ABIs are filtered out here to
        // keep the APK from carrying .so files nothing can load.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Inference runtime. onnxruntime-extensions is a companion AAR that adds
    // the custom operators the exported tokenizer graph depends on; without it
    // the session fails to load with "Unknown operator BertTokenizer".
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:0.13.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
}
