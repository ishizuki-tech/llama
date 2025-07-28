// =============================================================================
// Llama JNI bridge for Android
// =============================================================================
// JP: これは llama.cpp を Android (JNI) から呼び出すための最小限のブリッジ実装です。
//     - モデルのロード＆コンテキスト初期化
//     - 単一ターンのテキスト補完（プロンプト投入 -> サンプリング -> 出力）
//     - リソース解放
//
// EN: This is a minimal JNI bridge to use llama.cpp on Android.
//     - Load model & initialize a context
//     - Perform a single-turn text completion (prompt -> sample -> output)
//     - Free resources
//
// 設計 / Design notes
// JP:
// * llama_context はスレッドセーフではありません。Kotlin 側で単一スレッド Dispatcher を使い、
//   同一コンテキストへの呼び出しは直列化してください。
// * 生成ごとに Memory API で KV（全シーケンス）をクリアし、前回生成の痕跡を残さないようにします。
// * 乱数シードは Sampler 側（dist）で設定します（現行ヘッダでは llama_set_rng_seed は非推奨/無効）。
// * スレッド数は呼び出し毎に llama_set_n_threads() で調整可能です。
// EN:
// * llama_context is NOT thread-safe. On the Kotlin side, use a single-threaded
//   dispatcher and serialize calls to the same context.
// * Before each generation, we clear all KV memory via the Memory API so the
//   new prompt starts from a clean state.
// * Random seed is set via the sampler (dist). The function llama_set_rng_seed()
//   is deprecated/unavailable in recent headers.
// * Per-call threading can be changed via llama_set_n_threads().

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>  // std::min, std::max, std::clamp
#include <unistd.h>   // sysconf(_SC_NPROCESSORS_ONLN)
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "llama_jni", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  "llama_jni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "llama_jni", __VA_ARGS__)

// -----------------------------------------------------------------------------
// Internal handle: keep model/context/vocab together
// 内部ハンドル: モデル / コンテキスト / 語彙をまとめて保持
// -----------------------------------------------------------------------------
struct LlamaHandle {
    llama_model*       model  = nullptr;
    llama_context*     ctx    = nullptr;
    const llama_vocab* vocab  = nullptr;
    int                n_ctx  = 0;  // number of tokens in context window / コンテキスト長
};

// -----------------------------------------------------------------------------
// Helper: std::string -> jstring
// ヘルパ: std::string を jstring に変換
// -----------------------------------------------------------------------------
static inline jstring toJStr(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// -----------------------------------------------------------------------------
// JNI: Load model & initialize context
// JNI: モデルのロードとコンテキスト初期化
//
// jModelPath: Absolute path to GGUF file / GGUFファイルの絶対パス
// jNCtx     : Desired context length (<=0 -> 2048) / 希望コンテキスト長（0以下は 2048）
// -----------------------------------------------------------------------------
extern "C"
JNIEXPORT jlong JNICALL
Java_com_negi_nativelib_Llama_nativeLoadModel(
        JNIEnv* env, jobject /* thiz */, jstring jModelPath, jint jNCtx) {
    const char* cpath = env->GetStringUTFChars(jModelPath, nullptr);
    std::string path  = cpath ? cpath : "";
    env->ReleaseStringUTFChars(jModelPath, cpath);
    if (path.empty()) {
        LOGE("Model path is empty");
        return 0;
    }

    // JP: ggml/llama のグローバル初期化（通常はプロセス内で1回）
    // EN: Global initialization for ggml/llama (usually once per process).
    llama_backend_init();

    // JP: モデルをファイルから読み込み
    // EN: Load model from GGUF file.
    llama_model_params mp = llama_model_default_params();
    llama_model* model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) {
        LOGE("Failed to load model: %s", path.c_str());
        return 0;
    }

    // JP: 語彙ハンドル取得
    // EN: Get vocabulary handle for tokenization.
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // JP: コンテキスト作成（n_batch は n_ctx を上限に抑制）
    // EN: Create context; clamp n_batch to n_ctx for safety.
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx     = jNCtx > 0 ? (uint32_t) jNCtx : 2048;
    cp.n_batch   = std::min<uint32_t>(512, cp.n_ctx);
    cp.n_threads = std::max(2, (int)sysconf(_SC_NPROCESSORS_ONLN)); // can be overridden per-call
    cp.no_perf   = true; // reduce overhead on Android

    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("Failed to init context");
        llama_model_free(model);
        return 0;
    }

    auto* h = new LlamaHandle();
    h->model = model;
    h->ctx   = ctx;
    h->vocab = vocab;
    h->n_ctx = (int)cp.n_ctx;

    LOGI("Model loaded. n_ctx=%d, n_batch=%u, n_threads=%d",
         h->n_ctx, cp.n_batch, cp.n_threads);
    return reinterpret_cast<jlong>(h);
}

// -----------------------------------------------------------------------------
// JNI: Single-turn completion
// JNI: 単一ターン補完
//
// nThreads : per-call thread count (<=0 -> auto) / 呼び出し毎のスレッド数（0以下は自動）
// maxTokens: max tokens to generate / 生成最大トークン数
// temp     : sampling temperature / サンプリング温度
// topP     : nucleus sampling p / Top-p
// seed     : >=0 to fix RNG seed; negative -> default / 0以上で固定、負ならデフォルト
// -----------------------------------------------------------------------------
extern "C"
JNIEXPORT jstring JNICALL
Java_com_negi_nativelib_Llama_nativeCompletion(
        JNIEnv* env, jobject /* thiz */, jlong jHandle, jstring jPrompt,
        jint nThreads, jint maxTokens, jfloat temp, jfloat topP, jint seed) {

    auto* h = reinterpret_cast<LlamaHandle*>(jHandle);
    if (!h || !h->ctx || !h->model || !h->vocab) {
        LOGE("Invalid handle or context");
        return toJStr(env, "");
    }

    // JP: Java 文字列を std::string に変換
    // EN: Convert Java string to std::string.
    const char* cprompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt  = cprompt ? cprompt : "";
    env->ReleaseStringUTFChars(jPrompt, cprompt);
    if (prompt.empty()) {
        LOGW("Empty prompt");
        return toJStr(env, "");
    }

    // ---- Pre-generation setup / 生成前セットアップ ---------------------------

    // JP: Memory API を使って KV を全クリア（全シーケンス、[0, ∞)）
    // EN: Clear all KV memory (all sequences, from position 0 to end).
    // 安全に 0〜n の仮定で loop（例：0〜15）
    for (int seq_id = 0; seq_id < 16; ++seq_id) {
        llama_memory_seq_rm(llama_get_memory(h->ctx), seq_id, 0, -1);
    }

    // JP: 呼び出し毎のスレッド数（評価/バッチとも同値で十分）
    // EN: Per-call thread count (same value for eval & batch is usually fine).
    {
        int nth = (nThreads > 0)
                  ? (int)nThreads
                  : std::max(2, (int)sysconf(_SC_NPROCESSORS_ONLN));
        llama_set_n_threads(h->ctx, nth, nth);
        LOGI("Completion config: n_threads=%d, maxTokens=%d, temp=%.3f, topP=%.3f, seed=%d",
             nth, (int)maxTokens, (double)temp, (double)topP, (int)seed);
    }

    // JP: トークナイズ（まず必要サイズを取得してから二段階でトークナイズ）
    // EN: Tokenize prompt (two-step: query size first, then fill buffer).
    int n_prompt = -llama_tokenize(h->vocab, prompt.c_str(),
                                   (int)prompt.size(), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        LOGW("Tokenization resulted in <= 0 tokens");
        return toJStr(env, "");
    }
    std::vector<llama_token> toks(n_prompt);
    if (llama_tokenize(h->vocab, prompt.c_str(), (int)prompt.size(),
                       toks.data(), n_prompt, true, true) < 0) {
        LOGE("Tokenization failed");
        return toJStr(env, "");
    }

    // JP: コンテキスト長チェック（簡易）。必要なら predict をクリップしてください。
    // EN: Rough context length check. Clip 'predict' if needed.
    const int n_ctx   = h->n_ctx;
    const int predict = std::max(1, (int)maxTokens);
    if (n_prompt + predict + 8 > n_ctx) {
        LOGW("Potential context overflow: n_prompt=%d, n_predict=%d, n_ctx=%d",
             n_prompt, predict, n_ctx);
    }

    // JP: プロンプトをチャンクごとに decode（n_batch 単位）
    // EN: Decode prompt in chunks of size n_batch.
    const int n_batch = llama_n_batch(h->ctx);
    for (int i = 0; i < n_prompt; i += n_batch) {
        int m = std::min(n_batch, n_prompt - i);
        llama_batch batch = llama_batch_get_one(toks.data() + i, m);
        if (llama_decode(h->ctx, batch) != 0) {
            LOGE("llama_decode failed on prompt chunk");
            return toJStr(env, "");
        }
    }

    // ---- Sampler chain / サンプラーチェーン ---------------------------------

    // JP: temp/topP を正規化。topP==0 は未設定扱いで 0.95 に。
    // EN: Normalize temp/topP; treat topP==0 as unset, fallback to 0.95.
    float T = std::max(0.f, temp);
    float P = std::clamp(topP, 0.0f, 1.0f);
    if (P == 0.0f) P = 0.95f;

    auto chain_params = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(chain_params);
    if (!smpl) {
        LOGE("Failed to init sampler chain");
        return toJStr(env, "");
    }

    // JP: 乱数シードは確率サンプリング時に dist サンプラーで設定（greedy は不要）。
    // EN: Set RNG seed via 'dist' sampler only when using probabilistic sampling.
    uint32_t s = (seed >= 0) ? (uint32_t)seed : LLAMA_DEFAULT_SEED;

    if (T == 0.f) {
        // JP: 温度 0 → 完全決定論。greedy のみ。
        // EN: Temperature 0 -> deterministic; use greedy only.
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        // JP: 確率サンプリング。推奨順序: top-p → temp → dist(seed)
        // EN: Probabilistic sampling. Recommended order: top-p -> temp -> dist(seed)
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(P, /*min_keep=*/1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(T));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(s));
        // 例 / Example:
        // llama_sampler_chain_add(smpl, llama_sampler_init_penalty_repeat(...));
        // llama_sampler_chain_add(smpl, llama_sampler_init_penalty_frequency(...));
        // llama_sampler_chain_add(smpl, llama_sampler_init_penalty_presence(...));
    }

    // JP: 念のためチェーン状態を初期化
    // EN: Ensure sampler chain state is clean.
    llama_sampler_reset(smpl);

    // ---- Generation loop / 生成ループ ----------------------------------------

    std::string output;
    for (int t = 0; t < predict; ++t) {
        // JP: 次トークンをサンプリング
        // EN: Sample next token.
        llama_token tok = llama_sampler_sample(smpl, h->ctx, /*idx=*/-1);
        if (llama_vocab_is_eog(h->vocab, tok)) {
            // JP: EOS に達したら終了
            // EN: Stop if EOS is reached.
            break;
        }

        // JP: サンプラ履歴に受理（repeat penalty 等の将来追加に備える）
        // EN: Accept token into sampler history (useful for penalties).
        llama_sampler_accept(smpl, tok);

        // JP: トークンを文字列へ変換
        // EN: Convert token to piece/string.
        char buf[8192];
        int n = llama_token_to_piece(h->vocab, tok, buf, sizeof(buf),
                /*lstrip=*/0, /*special=*/true);
        if (n > 0) {
            output.append(buf, n);
        }

        // JP: 生成したトークンを次の入力として 1 トークンだけ decode
        // EN: Feed sampled token back into context (decode 1 token).
        llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(h->ctx, next) != 0) {
            LOGE("llama_decode failed during generation at step %d", t);
            break;
        }

        // JP: （任意）停止語判定やキャンセルポーリングはここに追加可能
        // EN: (Optional) Check stop-words or a cancel flag here.
    }

    // JP: サンプラー解放
    // EN: Free sampler chain.
    llama_sampler_free(smpl);

    // JP: 生成結果を Java 側に返却
    // EN: Return generated text to Java side.
    return toJStr(env, output);
}

// -----------------------------------------------------------------------------
// JNI: Free resources / リソース解放
// -----------------------------------------------------------------------------
extern "C"
JNIEXPORT void JNICALL
Java_com_negi_nativelib_Llama_nativeFree(
        JNIEnv*, jobject, jlong jHandle) {
    auto* h = reinterpret_cast<LlamaHandle*>(jHandle);
    if (!h) return;

    if (h->ctx)   { llama_free(h->ctx); h->ctx = nullptr; }
    if (h->model) { llama_model_free(h->model); h->model = nullptr; }

    delete h;

    // JP: 必要ならプロセス終了時に一度だけ llama_backend_free() を呼ぶ設計にできます。
    // EN: Optionally call llama_backend_free() once at process teardown if desired.
}
