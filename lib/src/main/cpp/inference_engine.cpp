// inference_engine.cpp — llama.cpp JNI wrapper for HY-MT translation
#include "logging.h"
#include <string>
#include <vector>
#include <cstring>
#include <thread>
#include <mutex>
#include <condition_variable>

// Forward declare llama.h types
struct llama_model;
struct llama_context;

static llama_model*  g_model   = nullptr;
static llama_context* g_ctx    = nullptr;
static std::mutex     g_mutex;
static bool           g_loading = false;

// HY-MT1.5 official prompt template (Chinese ↔ English translation)
// User: <im_start>system\nYou are a translator.<im_end>\n<im_start>user\nTranslate the following text from {src_lang} to {tgt_lang}.\n{text}<im_end>\nAssistant: <im_start>assistant\n
static std::string build_hymt_prompt(const std::string& text,
                                     const std::string& src_lang,
                                     const std::string& tgt_lang) {
    // src_lang / tgt_lang: "Chinese" or "English"
    std::string sys = "You are a professional translator. Translate the following text accurately and naturally.";
    std::string prompt;
    prompt.reserve(512);
    prompt += "<|im_start|>system\n" + sys + "<|im_end|>\n";
    prompt += "<|im_start|>user\n";
    prompt += "Translate the following text from " + src_lang + " to " + tgt_lang + ".\n";
    prompt += text + "\n";
    prompt += "<|im_end|>\n<|im_start|>assistant\n";
    return prompt;
}

static std::string llama_translate_impl(const std::string& text,
                                         const std::string& src_lang,
                                         const std::string& tgt_lang) {
    if (!g_model || !g_ctx) {
        return "Error: model not loaded";
    }

    const char* sys_prompt = "<|im_start|>system\nYou are a professional translator. Translate the following text accurately and naturally.<|im_end|>\n";

    llama_reset_timings(g_ctx);

    // Build prompt
    std::string user_prompt = "<|im_start|>user\nTranslate from " + src_lang + " to " + tgt_lang + ".\n" + text + "<|im_end|>\n<|im_start|>assistant\n";

    // Apply chat template
    std::vector<llama_token> embd_inp;
    llama->n_tokens = 0;
    // Simple: just tokenize the full prompt
    // We use llama_tokenize directly
    int n_tokens = llama_tokenize(g_model, (sys_prompt + user_prompt).c_str(), nullptr, 0, true);
    if (n_tokens <= 0) {
        return "Error: tokenization failed";
    }
    std::vector<llama_token> tokens(n_tokens);
    llama_tokenize(g_model, (sys_prompt + user_prompt).c_str(), tokens.data(), n_tokens, true);

    // Eval prompt
    for (int i = 0; i < (int)tokens.size(); i += 32) {
        int n_eval = std::min(32, (int)tokens.size() - i);
        if (llama_decode(g_ctx, llama_batch_get_one(&tokens[i], n_eval, i, 0))) {
            return "Error: prompt eval failed";
        }
    }

    // Generate completion (max 256 new tokens)
    std::string result;
    const int max_new = 256;
    llama_token new_token;
    for (int i = 0; i < max_new; i++) {
        new_token = llama_sample_token_greedy(g_ctx);
        if (new_token == llama_token_eos(g_model) || new_token == 2 /* </s> */) break;
        char buf[64];
        int n = llama_token_to_piece(g_model, new_token, buf, sizeof(buf), false, false);
        if (n > 0) result.append(buf, n);
        if (llama_decode(g_ctx, llama_batch_get_one(&new_token, 1, tokens.size() + i, 0))) {
            break;
        }
    }

    // Trim trailing whitespace / newlines
    while (!result.empty() && (result.back() == ' ' || result.back() == '\n')) result.pop_back();
    return result.empty() ? "(no translation)" : result;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_offlineinterpreter_lib_InferenceEngine_nativeTranslate(
        JNIEnv* env, jobject /*thiz*/,
        jstring j_text, jstring j_src_lang, jstring j_tgt_lang) {

    const char* c_text     = env->GetStringUTFChars(j_text,     nullptr);
    const char* c_src      = env->GetStringUTFChars(j_src_lang, nullptr);
    const char* c_tgt      = env->GetStringUTFChars(j_tgt_lang, nullptr);

    std::string result;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        result = llama_translate_impl(c_text, c_src, c_tgt);
    }

    env->ReleaseStringUTFChars(j_text,     c_text);
    env->ReleaseStringUTFChars(j_src_lang, c_src);
    env->ReleaseStringUTFChars(j_tgt_lang, c_tgt);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_offlineinterpreter_lib_InferenceEngine_nativeLoadModel(
        JNIEnv* env, jobject /*thiz*/, jstring j_path) {

    const char* c_path = env->GetStringUTFChars(j_path, nullptr);
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    if (g_ctx) {
        llama_free_ctx(g_ctx);
        g_ctx = nullptr;
    }

    LOGI("Loading model from: %s", c_path);
    g_model = llama_load_model_from_file(c_path, {
        .n_ctx_train   = 8192,
        .n_ctx         = 2048,
        .n_gpu_layers  = 100,   // offload all to GPU/ANE
        .main_gpu      = 0,
        .tensor_split  = nullptr,
        .rope_freq_base = 0,
        .rope_freq_scale = 0,
        .n_threads     = 4,
        .n_threads_batch = 4,
    });
    if (!g_model) {
        LOGE("Failed to load model: %s", c_path);
        env->ReleaseStringUTFChars(j_path, c_path);
        return JNI_FALSE;
    }

    g_ctx = llama_new_context_with_model(g_model, {
        .n_ctx       = 2048,
        .n_batch     = 512,
        .n_threads   = 4,
        .n_threads_batch = 4,
    });
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(j_path, c_path);
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    env->ReleaseStringUTFChars(j_path, c_path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_offlineinterpreter_lib_InferenceEngine_nativeIsModelLoaded(
        JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_offlineinterpreter_lib_InferenceEngine_nativeUnloadModel(
        JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx)  { llama_free_ctx(g_ctx);  g_ctx  = nullptr; }
    if (g_model){ llama_free_model(g_model); g_model = nullptr; }
    LOGI("Model unloaded");
}

} // extern "C"
