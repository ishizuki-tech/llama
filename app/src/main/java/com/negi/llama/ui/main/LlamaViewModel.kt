// LlamaViewModel.kt
package com.negi.llama.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.negi.nativelib.LlamaContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val LOG_TAG = "LlamaVM"

/**
 * EN: ViewModel owning a single native `LlamaContext`.
 *     - `LlamaContext` (llama.cpp) is NOT thread-safe; the wrapper serializes calls.
 *     - Exposes a simple UI state for the screen to observe.
 *
 * JP: ネイティブ `LlamaContext` を 1 つ保持する ViewModel。
 *     - `LlamaContext` はスレッドセーフではないため、ラッパー側で直列化します。
 *     - 画面が監視するための簡潔な UI 状態を公開します。
 */
class LlamaViewModel(app: Application) : AndroidViewModel(app) {

    // Keep an application reference to avoid repeated generic casts.
    // 毎回のジェネリックキャストを避けるため Application を保持。
    private val application: Application = app

    // Native context (created on demand; freed on clear/release).
    // ネイティブコンテキスト（必要時に生成し、破棄時に解放）
    private var llama: LlamaContext? = null

    // UI state (single source of truth).
    // UI 状態（単一の情報源）
    private val _state = MutableStateFlow(LlamaUiState(isModelLoading = true))
    val state: StateFlow<LlamaUiState> = _state

    // Track current generation to support cancel.
    // 現在の生成処理を保持（キャンセル対応）
    private var genJob: Job? = null

    /**
     * EN: Load the model if not loaded yet.
     *     - If [assetPath] is null, auto-detect a `.gguf` under `assets/models`,
     *       preferring lighter quants first (Q4_K_M -> Q4_K_S -> ...).
     *     - Set [forceReload]=true to rebuild context even if it's already loaded.
     *
     * JP: モデルが未ロードなら読み込みます。
     *     - [assetPath] が null の場合、`assets/models` から `.gguf` を自動検出し、
     *       軽い量子化を優先（Q4_K_M → Q4_K_S → …）して 1 つ選択します。
     *     - 既にロード済みでも [forceReload]=true で再作成可能です。
     *
     * 例) assetPath: "models/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
     */
    fun loadModelIfNeeded(
        assetPath: String? = null,
//        nCtx: Int = 2048,
//        nCtx: Int = 1024,
//        nCtx: Int = 512,
        nCtx: Int = 256,
        overwrite: Boolean = false,
        forceReload: Boolean = false
    ) {
        if (llama != null && !forceReload) {
            // Already loaded; ensure flags are consistent.
            // 既にロード済み。フラグ整合のみ行う。
            if (!_state.value.ready) {
                _state.update { it.copy(isModelLoading = false, ready = true) }
            }
            return
        }

        _state.update { it.copy(isModelLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // 1) If file path is an absolute filesystem path, load directly from file.
                // 1) 絶対パスならファイルから直接ロード
                val ctx = when {
                    assetPath?.startsWith("/") == true || assetPath?.startsWith("file:") == true -> {
                        val path = assetPath.removePrefix("file:")
                        require(File(path).exists()) {
                            "File not found: $path"
                        }
                        Log.d(LOG_TAG, "Loading model from file: $path")
                        LlamaContext.createContextFromFile(path, nCtx)
                    }

                    else -> {
                        // 2) Asset path was provided -> use it as-is.
                        // 2) assetPath が指定されていればそのまま使用
                        val resolvedAssetPath = assetPath ?: pickGgufFromAssets(application)
                        ?: throw IllegalStateException(
                            "No .gguf found under assets/models. " +
                                    "Place a model at app/src/main/assets/models/*.gguf"
                        )
                        Log.d(LOG_TAG, "Loading model from asset: $resolvedAssetPath")
                        LlamaContext.createContextFromAsset(
                            context = application,
                            assetPath = resolvedAssetPath,
                            nCtx = nCtx,
                            overwrite = overwrite
                        )
                    }
                }

                // If reloading, close the old one first.
                // 再読み込み時は古いコンテキストを先に閉じる
                llama?.close()
                llama = ctx

                _state.update { it.copy(isModelLoading = false, ready = true) }
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Model load failed", t)
                _state.update {
                    it.copy(
                        isModelLoading = false,
                        ready = false,
                        error = "モデルのロードに失敗: ${t.message}"
                    )
                }
            }
        }
    }

    /** EN: Update prompt text. / JP: プロンプト文字列を更新します。 */
    fun updatePrompt(text: String) {
        _state.update { it.copy(prompt = text) }
    }

    /**
     * EN: Run a completion. Cancels any ongoing job to prevent overlap.
     * JP: 補完を実行。二重起動を避けるため、実行中のジョブがあればキャンセルします。
     */
    fun generate(
        maxTokens: Int = 128,
        temperature: Float = 0.8f,
        topP: Float = 0.95f,
        seed: Int = -1
    ) {
        val ctx = llama ?: run {
            _state.update { it.copy(error = "モデルが未ロードです") }
            return
        }
        val prompt = _state.value.prompt
        if (prompt.isBlank()) return

        // Prevent double-start.
        // 二重起動防止
        genJob?.cancel()

        _state.update { it.copy(isGenerating = true, error = null) }
        genJob = viewModelScope.launch {
            try {
                val out = ctx.complete(
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topP = topP,
                    seed = seed
                )
                _state.update { it.copy(isGenerating = false, output = out) }
            } catch (ce: CancellationException) {
                // User cancelled; just reset the flag.
                // ユーザーキャンセル時：フラグのみ戻す
                _state.update { it.copy(isGenerating = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(isGenerating = false, error = "生成エラー: ${t.message}") }
            }
        }
    }

    /** EN: Cancel current generation (if any). / JP: 実行中の生成処理をキャンセルします。 */
    fun cancelGeneration() {
        genJob?.cancel()
    }

    /**
     * EN: Manually release native resources (free memory earlier than onCleared()).
     * JP: ネイティブリソースを明示解放（onCleared() より前にメモリを空けたい場合）。
     */
    fun releaseModel() {
        genJob?.cancel()
        llama?.close()
        llama = null
        _state.update { it.copy(ready = false) }
    }

    /**
     * EN: Ensure native resources are freed on ViewModel destroy.
     * JP: ViewModel 破棄時にネイティブリソースを確実に解放。
     */
    override fun onCleared() {
        genJob?.cancel()
        llama?.close()
        llama = null
        super.onCleared()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * EN: Pick one `.gguf` from `assets/models` with quant preference.
     *     Returns the asset-relative path like "models/<file>.gguf".
     *
     * JP: `assets/models` から量子化の優先順に 1 つ選択し、
     *     "models/<file>.gguf" のような assets 相対パスを返します。
     */
    private fun pickGgufFromAssets(app: Application): String? {
        val dir = "models"
        val files = app.assets.list(dir).orEmpty()
        if (files.isEmpty()) {
            Log.w(LOG_TAG, "No files under assets/$dir")
            return null
        }
        Log.d(LOG_TAG, "assets/$dir = ${files.joinToString()}")

        // Prefer lighter quants first. ハイフン/ドットどちらの命名にもゆるくマッチ。
        val prefer = listOf(
            "Q4_K_M", "Q4_K_S", "Q4_K_L",
            "Q5_K_M", "Q5_K_S",
            "Q8_0", "Q6_K", "Q3_K_XL"
        )

        val gguf = files.filter { it.endsWith(".gguf") }
        // Try by preference
        for (p in prefer) {
            val hit = gguf.firstOrNull { it.contains("-$p.") || it.contains(".$p.") }
            if (hit != null) return "$dir/$hit"
        }
        // Fallback to the first gguf
        return gguf.firstOrNull()?.let { "$dir/$it" }
    }
}
