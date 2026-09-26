// JNI bridge over stable-diffusion.cpp.
//
// Design notes:
//  * One `Session` per loaded model holds the sd_ctx_t. The API is synchronous,
//    so Kotlin runs it on a dedicated single thread (asCoroutineDispatcher) and
//    only one generation ever touches the context at a time.
//  * The result is returned as an ARGB int[] rather than a Bitmap so the JNI
//    layer stays free of jnigraphics; Kotlin turns the pixels into a Bitmap.
//  * Progress is reported through a single global sd_progress callback. The
//    callback fires on the thread that called generate_image, which is already
//    attached to the JVM, so attaching there is cheap and safe.

#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "stable-diffusion.h"

#define LOG_TAG "AillmDiffusion"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    sd_ctx_t * ctx = nullptr;
    int last_width = 0;
    int last_height = 0;
};

JavaVM * g_vm = nullptr;

std::mutex g_listener_mutex;
jobject g_listener = nullptr;          // DiffusionProgressListener, global ref
jmethodID g_on_progress = nullptr;

std::string to_std_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return std::string();
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = (chars != nullptr) ? std::string(chars) : std::string();
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

void log_callback(enum sd_log_level_t level, const char * text, void *) {
    if (text == nullptr) {
        return;
    }
    if (level == SD_LOG_ERROR) {
        LOGE("%s", text);
    } else if (level <= SD_LOG_WARN) {
        LOGI("%s", text);
    }
}

void progress_callback(int step, int steps, float time, void *) {
    std::lock_guard<std::mutex> lock(g_listener_mutex);
    if (g_listener == nullptr || g_on_progress == nullptr || g_vm == nullptr) {
        return;
    }
    JNIEnv * env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) {
            return;
        }
        attached = true;
    }
    env->CallVoidMethod(g_listener, g_on_progress, step, steps, time);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeLoad(
        JNIEnv * env, jobject, jstring j_path, jint n_threads, jint wtype, jboolean mmap) {
    const std::string path = to_std_string(env, j_path);
    if (path.empty()) {
        return 0;
    }

    sd_set_log_callback(log_callback, nullptr);
    sd_set_progress_callback(progress_callback, nullptr);

    auto * session = new Session();

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path  = path.c_str();
    params.n_threads   = static_cast<int>(n_threads);
    params.wtype       = static_cast<enum sd_type_t>(wtype);
    params.enable_mmap = (mmap == JNI_TRUE);
    params.flash_attn  = false;

    session->ctx = new_sd_ctx(&params);
    if (session->ctx == nullptr) {
        LOGE("new_sd_ctx failed for %s", path.c_str());
        delete session;
        return 0;
    }
    LOGI("loaded diffusion model: %s", path.c_str());
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) {
        return;
    }
    if (session->ctx != nullptr) {
        free_sd_ctx(session->ctx);
    }
    delete session;
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeCancel(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session != nullptr && session->ctx != nullptr) {
        sd_cancel_generation(session->ctx, SD_CANCEL_ALL);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeLastWidth(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    return session != nullptr ? session->last_width : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeLastHeight(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    return session != nullptr ? session->last_height : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeSetProgressListener(
        JNIEnv * env, jobject, jobject listener) {
    std::lock_guard<std::mutex> lock(g_listener_mutex);
    if (g_listener != nullptr) {
        env->DeleteGlobalRef(g_listener);
        g_listener = nullptr;
        g_on_progress = nullptr;
    }
    if (listener != nullptr) {
        g_listener = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        if (cls != nullptr) {
            g_on_progress = env->GetMethodID(cls, "onProgress", "(IIF)V");
            env->DeleteLocalRef(cls);
        }
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_goldmedal_aillm_ai_diffusion_DiffusionNative_nativeGenerate(
        JNIEnv * env, jobject,
        jlong handle, jstring j_prompt, jstring j_negative,
        jint width, jint height, jint steps, jfloat guidance, jlong seed) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr) {
        return nullptr;
    }

    const std::string prompt   = to_std_string(env, j_prompt);
    const std::string negative = to_std_string(env, j_negative);
    if (prompt.empty()) {
        return nullptr;
    }

    sd_img_gen_params_t params;
    sd_img_gen_params_init(&params);
    sd_sample_params_init(&params.sample_params);
    params.prompt                    = prompt.c_str();
    params.negative_prompt           = negative.c_str();
    params.width                     = static_cast<int>(width);
    params.height                    = static_cast<int>(height);
    params.seed                      = static_cast<int64_t>(seed);
    params.batch_count               = 1;
    params.sample_params.sample_steps       = static_cast<int>(steps);
    params.sample_params.guidance.txt_cfg   = guidance;
    // Absolute Reality is an SD 1.5 checkpoint; DPM++ 2M Karras is the sampler
    // it was tuned with.
    params.sample_params.sample_method      = DPMPP2M_SAMPLE_METHOD;
    params.sample_params.scheduler          = KARRAS_SCHEDULER;

    sd_image_t * images = nullptr;
    int count = 0;
    const bool ok = generate_image(session->ctx, &params, &images, &count);
    if (!ok || count <= 0 || images == nullptr || images[0].data == nullptr) {
        if (images != nullptr) {
            free_sd_images(images, count);
        }
        return nullptr;
    }

    const uint32_t out_w = images[0].width;
    const uint32_t out_h = images[0].height;
    const uint32_t channels = images[0].channel;
    session->last_width = static_cast<int>(out_w);
    session->last_height = static_cast<int>(out_h);

    const jsize pixel_count = static_cast<jsize>(out_w * out_h);
    std::vector<jint> pixels(pixel_count);
    const uint8_t * data = images[0].data;
    for (uint32_t i = 0; i < out_w * out_h; ++i) {
        const uint8_t r = data[i * channels + 0];
        const uint8_t g = (channels > 1) ? data[i * channels + 1] : r;
        const uint8_t b = (channels > 2) ? data[i * channels + 2] : r;
        pixels[i] = static_cast<jint>(0xFF000000u) |
                    (static_cast<jint>(r) << 16) |
                    (static_cast<jint>(g) << 8) |
                    static_cast<jint>(b);
    }

    jintArray result = env->NewIntArray(pixel_count);
    if (result != nullptr) {
        env->SetIntArrayRegion(result, 0, pixel_count, pixels.data());
    }
    free_sd_images(images, count);
    return result;
}
