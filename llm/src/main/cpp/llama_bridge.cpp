// JNI bridge over llama.cpp (+ libmtmd for vision).
//
// Design notes:
//  * One `Session` per loaded model holds the weights; the context and sampler
//    are created per generation and torn down afterwards. That keeps memory
//    predictable and means we never have to reason about KV-cache state carried
//    across turns (we re-feed the whole prompt each time anyway).
//  * A vision projector (mmproj) is attached to the *same* session as the text
//    model, because that is what a multimodal GGUF actually is: a text model
//    plus a separately loaded vision encoder.
//  * Generation is pull-based: `beginGeneration` prepares, `nextToken` is called
//    repeatedly (each call decodes then samples), `endGeneration` releases. A
//    pull API means Kotlin can emit into a Flow with natural back-pressure and
//    cancellation, with no native -> JVM callback threading to get wrong.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "AillmLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kDefaultMaxContext = 4096;
constexpr int kMaxBatch = 512;

struct Session {
    llama_model * model = nullptr;
    const llama_vocab * vocab = nullptr;
    mtmd_context * mctx = nullptr;   // vision/audio projector, optional

    // Per-generation state.
    llama_context * ctx = nullptr;
    llama_sampler * smpl = nullptr;
    std::vector<llama_token> pending;   // tokens decoded on the next `nextToken`
    bool active = false;
    int max_tokens = 0;
    int produced = 0;
    int n_batch = kMaxBatch;

    int max_context = kDefaultMaxContext;
    int n_threads = 4;
};

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

void free_generation(Session * session) {
    if (session->smpl != nullptr) {
        llama_sampler_free(session->smpl);
        session->smpl = nullptr;
    }
    if (session->ctx != nullptr) {
        llama_free(session->ctx);
        session->ctx = nullptr;
    }
    session->pending.clear();
    session->active = false;
    session->produced = 0;
}

void free_projector(Session * session) {
    if (session->mctx != nullptr) {
        mtmd_free(session->mctx);
        session->mctx = nullptr;
    }
}

/** Creates the sampler chain for one generation. */
bool setup_sampler(Session * session, float temperature, float top_p, int top_k, int seed) {
    llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
    chain_params.no_perf = true;
    session->smpl = llama_sampler_chain_init(chain_params);
    if (session->smpl == nullptr) {
        return false;
    }
    if (top_k > 0) {
        llama_sampler_chain_add(session->smpl, llama_sampler_init_top_k(top_k));
    }
    if (top_p > 0.0f && top_p < 1.0f) {
        llama_sampler_chain_add(session->smpl, llama_sampler_init_top_p(top_p, 1));
    }
    if (temperature > 0.01f) {
        llama_sampler_chain_add(session->smpl, llama_sampler_init_temp(temperature));
    } else {
        llama_sampler_chain_add(session->smpl, llama_sampler_init_greedy());
    }
    llama_sampler_chain_add(session->smpl, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    return true;
}

bool setup_context(Session * session, int n_ctx) {
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::max(256, n_ctx));
    ctx_params.n_batch = static_cast<uint32_t>(std::min(std::max(256, n_ctx), kMaxBatch));
    ctx_params.n_threads = session->n_threads;
    ctx_params.n_threads_batch = session->n_threads;
    ctx_params.no_perf = true;

    session->ctx = llama_init_from_model(session->model, ctx_params);
    if (session->ctx == nullptr) {
        return false;
    }
    session->n_batch = static_cast<int>(ctx_params.n_batch);
    return true;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_backendInit(JNIEnv *, jobject) {
    static std::once_flag once;
    std::call_once(once, []() {
        ggml_backend_load_all();
        llama_log_set([](ggml_log_level level, const char * text, void *) {
            if (level == GGML_LOG_LEVEL_ERROR) {
                LOGE("%s", text);
            }
        }, nullptr);
        mtmd_helper_log_set([](ggml_log_level level, const char * text, void *) {
            if (level == GGML_LOG_LEVEL_ERROR) {
                LOGE("%s", text);
            }
        }, nullptr);
    });
}

JNIEXPORT jlong JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_loadModel(
        JNIEnv * env, jobject,
        jstring j_path, jint max_context, jint threads, jint gpu_layers) {

    const std::string path = to_std_string(env, j_path);
    if (path.empty()) {
        LOGE("loadModel: empty path");
        return 0;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpu_layers;

    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        LOGE("loadModel: failed to load %s", path.c_str());
        return 0;
    }

    auto * session = new Session();
    session->model = model;
    session->vocab = llama_model_get_vocab(model);
    session->max_context = std::max(512, static_cast<int>(max_context));
    session->n_threads = std::max(1, static_cast<int>(threads));

    LOGI("loadModel: loaded %s", path.c_str());
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_freeModel(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) {
        return;
    }
    free_generation(session);
    free_projector(session);
    if (session->model != nullptr) {
        llama_model_free(session->model);
        session->model = nullptr;
    }
    delete session;
}

JNIEXPORT jboolean JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_loadProjector(
        JNIEnv * env, jobject, jlong handle, jstring j_path) {

    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->model == nullptr) {
        return JNI_FALSE;
    }

    free_projector(session);

    const std::string path = to_std_string(env, j_path);
    if (path.empty()) {
        return JNI_FALSE;
    }

    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = false;
    params.print_timings = false;
    params.n_threads = session->n_threads;

    session->mctx = mtmd_init_from_file(path.c_str(), session->model, params);
    if (session->mctx == nullptr) {
        LOGE("loadProjector: failed to load %s", path.c_str());
        return JNI_FALSE;
    }
    if (!mtmd_support_vision(session->mctx)) {
        // Audio-only projectors are not usable by the vision path.
        LOGE("loadProjector: projector has no vision support");
        free_projector(session);
        return JNI_FALSE;
    }
    LOGI("loadProjector: loaded %s", path.c_str());
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_defaultMarker(JNIEnv * env, jobject) {
    const char * marker = mtmd_default_marker();
    return env->NewStringUTF(marker != nullptr ? marker : "<__media__>");
}

JNIEXPORT jint JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_beginGeneration(
        JNIEnv * env, jobject, jlong handle, jstring j_prompt,
        jint max_tokens, jfloat temperature, jfloat top_p, jint top_k, jint seed) {

    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->model == nullptr || session->vocab == nullptr) {
        return -1; // no model
    }

    free_generation(session);

    const std::string prompt = to_std_string(env, j_prompt);
    if (prompt.empty()) {
        return -2; // empty prompt
    }

    const int n_prompt = -llama_tokenize(
            session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            nullptr, 0, /*add_special=*/true, /*parse_special=*/true);
    if (n_prompt <= 0) {
        return -3; // tokenization failed
    }
    if (n_prompt + 4 > session->max_context) {
        LOGE("beginGeneration: prompt of %d tokens exceeds context %d", n_prompt, session->max_context);
        return -4; // prompt longer than the context we may allocate
    }

    std::vector<llama_token> tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(
            session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()),
            /*add_special=*/true, /*parse_special=*/true) < 0) {
        return -3;
    }

    const int n_ctx = std::min(n_prompt + static_cast<int>(max_tokens), session->max_context);
    if (!setup_context(session, n_ctx)) {
        LOGE("beginGeneration: failed to create context");
        return -5;
    }
    if (!setup_sampler(session, temperature, top_p, top_k, seed)) {
        free_generation(session);
        return -6;
    }

    session->pending.swap(tokens);
    session->max_tokens = std::max(1, static_cast<int>(max_tokens));
    session->produced = 0;
    session->active = true;

    LOGI("beginGeneration: %d prompt tokens, n_ctx=%d", n_prompt, session->ctx != nullptr ? n_ctx : 0);
    return n_prompt;
}

JNIEXPORT jint JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_beginVisionGeneration(
        JNIEnv * env, jobject, jlong handle, jstring j_prompt, jbyteArray j_image,
        jint max_tokens, jfloat temperature, jfloat top_p, jint top_k, jint seed) {

    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->model == nullptr) {
        return -1; // no model
    }
    if (session->mctx == nullptr) {
        return -10; // no projector attached
    }
    if (j_image == nullptr) {
        return -12; // no image
    }

    free_generation(session);

    // Decode the attached image straight from memory.
    const jsize byte_count = env->GetArrayLength(j_image);
    if (byte_count <= 0) {
        return -12;
    }
    std::vector<unsigned char> image_bytes(static_cast<size_t>(byte_count));
    env->GetByteArrayRegion(
            j_image, 0, byte_count, reinterpret_cast<jbyte *>(image_bytes.data()));

    mtmd_helper_init_opt init_opt = mtmd_helper_init_opt_default();
    mtmd_helper_bitmap_wrapper wrapper = mtmd_helper_bitmap_init_from_buf(
            session->mctx, image_bytes.data(), image_bytes.size(),
            /*placeholder=*/false, init_opt);
    if (wrapper.bitmap == nullptr) {
        LOGE("beginVisionGeneration: could not decode the image");
        return -12;
    }

    const std::string prompt = to_std_string(env, j_prompt);
    mtmd_input_text input;
    input.text = prompt.c_str();
    input.text_len = prompt.size();
    input.add_special = true;
    input.parse_special = true;

    const mtmd_bitmap * bitmaps[1] = { wrapper.bitmap };
    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    if (chunks == nullptr) {
        mtmd_bitmap_free(wrapper.bitmap);
        return -13;
    }

    const int32_t tokenize_result = mtmd_tokenize(session->mctx, chunks, &input, bitmaps, 1);
    if (tokenize_result != 0) {
        LOGE("beginVisionGeneration: mtmd_tokenize failed (%d)", tokenize_result);
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(wrapper.bitmap);
        return -13;
    }

    const int n_pos = static_cast<int>(mtmd_helper_get_n_pos(chunks));
    const int n_ctx = std::min(n_pos + static_cast<int>(max_tokens) + 8, session->max_context);
    if (!setup_context(session, n_ctx)) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(wrapper.bitmap);
        return -5;
    }
    if (!setup_sampler(session, temperature, top_p, top_k, seed)) {
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(wrapper.bitmap);
        free_generation(session);
        return -6;
    }

    // Encodes the image with the projector and feeds everything through the
    // text model, leaving logits for the last position.
    llama_pos new_n_past = 0;
    const int32_t eval_result = mtmd_helper_eval_chunks(
            session->mctx, session->ctx, chunks,
            /*n_past=*/0, /*seq_id=*/0, session->n_batch,
            /*logits_last=*/true, &new_n_past);

    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(wrapper.bitmap);

    if (eval_result != 0) {
        LOGE("beginVisionGeneration: mtmd_helper_eval_chunks failed (%d)", eval_result);
        free_generation(session);
        return -14;
    }

    // The KV cache is already primed, so the next call samples immediately.
    session->pending.clear();
    session->max_tokens = std::max(1, static_cast<int>(max_tokens));
    session->produced = 0;
    session->active = true;

    LOGI("beginVisionGeneration: %d positions, n_ctx=%d", n_pos, n_ctx);
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_nextToken(JNIEnv * env, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || !session->active || session->ctx == nullptr || session->smpl == nullptr) {
        return nullptr;
    }
    if (session->produced >= session->max_tokens) {
        return nullptr;
    }

    // Decode whatever is still queued (the prompt on the first call, then one
    // token per call afterwards) in batches the context can handle.
    if (!session->pending.empty()) {
        size_t offset = 0;
        while (offset < session->pending.size()) {
            const int chunk = static_cast<int>(std::min(
                    static_cast<size_t>(session->n_batch),
                    session->pending.size() - offset));
            llama_batch batch = llama_batch_get_one(session->pending.data() + offset, chunk);
            if (llama_decode(session->ctx, batch) != 0) {
                LOGE("nextToken: llama_decode failed");
                return nullptr;
            }
            offset += static_cast<size_t>(chunk);
        }
        session->pending.clear();
    }

    const llama_token id = llama_sampler_sample(session->smpl, session->ctx, -1);
    if (llama_vocab_is_eog(session->vocab, id)) {
        return nullptr;
    }

    char buffer[512];
    const int written = llama_token_to_piece(
            session->vocab, id, buffer, static_cast<int32_t>(sizeof(buffer)), 0, true);
    if (written < 0) {
        return nullptr;
    }

    session->pending.assign(1, id);
    session->produced += 1;

    return env->NewStringUTF(std::string(buffer, static_cast<size_t>(written)).c_str());
}

JNIEXPORT void JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_endGeneration(JNIEnv *, jobject, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) {
        return;
    }
    free_generation(session);
}

JNIEXPORT jint JNICALL
Java_com_goldmedal_aillm_ai_llm_LlamaNative_countTokens(JNIEnv * env, jobject, jlong handle, jstring j_text) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->vocab == nullptr) {
        return -1;
    }
    const std::string text = to_std_string(env, j_text);
    if (text.empty()) {
        return 0;
    }
    return -llama_tokenize(
            session->vocab, text.c_str(), static_cast<int32_t>(text.size()),
            nullptr, 0, /*add_special=*/false, /*parse_special=*/true);
}

} // extern "C"
