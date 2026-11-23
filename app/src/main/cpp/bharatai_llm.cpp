#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include <algorithm>
#include "llama.h"

// Simple CPU-only inference bridge; one serialized session. Based on llama.cpp simple example.
// Important: this is source-level integration; an actual Android NDK build is still required.
namespace {
std::mutex mu;
llama_model *model = nullptr;
llama_context *ctx = nullptr;
bool backend_initialized = false;

std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *p = env->GetStringUTFChars(s, nullptr);
    if (!p) throw std::runtime_error("Unable to read UTF-8 string");
    std::string v(p);
    env->ReleaseStringUTFChars(s, p);
    return v;
}
void reset_model() {
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
}
jstring fail(JNIEnv *env, const std::string &msg) {
    return env->NewStringUTF(("ERROR: " + msg).c_str());
}
void load(const std::string &path, int nctx, int threads) {
    reset_model();
    if (!backend_initialized) { llama_backend_init(); backend_initialized = true; }
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU-only: no assumptions about the student's GPU.
    model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) throw std::runtime_error("llama.cpp could not load GGUF: ensure it is compatible, complete, and readable");
    auto cp = llama_context_default_params();
    cp.n_ctx = std::max(512, std::min(nctx, 2048));
    cp.n_batch = 256;
    cp.n_ubatch = 256;
    cp.n_threads = std::max(1, std::min(threads, 8));
    ctx = llama_init_from_model(model, cp);
    if (!ctx) { reset_model(); throw std::runtime_error("Unable to allocate model context; check available RAM"); }
}

void set_batch(llama_batch_ext *batch, const llama_token *tokens, int count, int position) {
    llama_batch_ext_clear(batch);
    for (int i = 0; i < count; ++i) {
        const int index = llama_batch_ext_add_token(batch, 0, tokens[i]);
        llama_pos p = position + i;
        llama_batch_ext_set_pos(batch, index, &p);
    }
    llama_batch_ext_set_output_logits(batch, count - 1, true);
}

std::string complete(const std::string &prompt, int limit) {
    if (!model || !ctx) throw std::runtime_error("Load the GGUF first");
    const int max_output = std::max(16, std::min(limit, 384));
    const auto *vocab = llama_model_get_vocab(model);
    // Qwen3 1.7B: non-thinking user turn; no server, chat-template dependencies, or network.
    const std::string formatted = "<|im_start|>system\nYou are BharatAI's input extractor. Respond with JSON only. Do not think aloud.<|im_end|>\n"
                                  "<|im_start|>user\n" + prompt + "\n/no_think<|im_end|>\n<|im_start|>assistant\n";
    int required = -llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()), nullptr, 0, true, true);
    if (required <= 0 || required >= 1536) throw std::runtime_error("Prompt too long for mobile context");
    std::vector<llama_token> tokens(required);
    if (llama_tokenize(vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()), tokens.data(), required, true, true) < 0)
        throw std::runtime_error("Tokenization failed");
    if (required + max_output >= static_cast<int>(llama_n_ctx(ctx)))
        throw std::runtime_error("Question exceeds loaded model context; shorten input");

    llama_memory_clear(llama_get_memory(ctx), false);
    llama_batch_ext *batch = llama_batch_ext_init(ctx);
    if (!batch) throw std::runtime_error("Could not allocate token batch");
    auto *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) { llama_batch_ext_free(batch); throw std::runtime_error("Could not create sampler"); }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    std::string output;
    try {
        int pos = 0;
        while (pos < required) {
            const int n = std::min(256, required - pos);
            set_batch(batch, tokens.data() + pos, n, pos);
            if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0)
                throw std::runtime_error("Model prompt evaluation failed");
            pos += n;
        }
        for (int i = 0; i < max_output; ++i) {
            llama_token t = llama_sampler_sample(sampler, ctx, -1);
            if (llama_vocab_is_eog(vocab, t)) break;
            std::vector<char> piece(512);
            int n = llama_token_to_piece(vocab, t, piece.data(), piece.size(), 0, true);
            if (n < 0) {
                piece.resize(static_cast<size_t>(-n) + 2);
                n = llama_token_to_piece(vocab, t, piece.data(), piece.size(), 0, true);
            }
            if (n > 0) output.append(piece.data(), static_cast<size_t>(n));
            if (i + 1 >= max_output) break;
            set_batch(batch, &t, 1, pos++);
            if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0)
                throw std::runtime_error("Model generation failed");
        }
    } catch (...) {
        llama_sampler_free(sampler);
        llama_batch_ext_free(batch);
        throw;
    }
    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    return output;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_bharatai_education_localai_NativeLlama_initialize(JNIEnv *env, jobject, jstring path, jint context, jint threads) {
    std::lock_guard<std::mutex> lock(mu);
    try { load(jstr(env, path), context, threads); return env->NewStringUTF("Model loaded on device (CPU)"); }
    catch (const std::exception &e) { reset_model(); return fail(env, e.what()); }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_bharatai_education_localai_NativeLlama_generate(JNIEnv *env, jobject, jstring prompt, jint tokens) {
    std::lock_guard<std::mutex> lock(mu);
    try { return env->NewStringUTF(complete(jstr(env, prompt), tokens).c_str()); }
    catch (const std::exception &e) { return fail(env, e.what()); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_bharatai_education_localai_NativeLlama_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(mu);
    reset_model();
}
