package com.negi.nativelib

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

private const val LOG_TAG = "LibLlama"

/**
 * =============================================================================
 * LlamaContext (Kotlin-side wrapper around a native llama.cpp context)
 * =============================================================================
 * JP: llama.cpp のコンテキストはスレッドセーフではありません。このラッパーは
 *     単一スレッドDispatcher上でネイティブ呼び出しを逐次実行します。
 *
 * EN: llama.cpp contexts are NOT thread-safe. This wrapper serializes all native
 *     calls onto a dedicated single-threaded dispatcher.
 *
 * 運用 / Usage:
 * - createContextFromFile / createContextFromAsset で生成
 * - complete() で補完（サスペンド関数）
 * - 使い終わったら close()/release() で明示的に解放（重要）
 */
class LlamaContext private constructor(private var handle: Long) : AutoCloseable {

    /** JP: ネイティブ呼び出し専用スレッド / EN: dedicated thread for native calls */
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /** JP: ライフサイクル管理用（将来拡張のため）/ EN: lifecycle scope (for future use) */
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /**
     * JP: テキスト補完を実行します。ネイティブ側でスレッド数、温度、top-p、seed を使用します。
     * EN: Perform a text completion. Native side consumes threads, temperature, top-p, seed.
     */
    suspend fun complete(
        prompt: String,
        maxTokens: Int = 32,
        temperature: Float = 0.8f,
        topP: Float = 0.95f,
        seed: Int = -1
    ): String = withContext(dispatcher) {
        check(handle != 0L) { "Context not initialized" }
//        val nThreads = LlamaCpuConfig.preferredThreadCount
        val nThreads = 1
        Log.d(LOG_TAG, "Using $nThreads threads for completion")
        LlamaLib.nativeCompletion(handle, prompt, nThreads, maxTokens, temperature, topP, seed)
    }

    /**
     * JP: ネイティブリソースを解放します（サスペンド版）。
     * EN: Release native resources (suspending).
     */
    suspend fun release() = withContext(dispatcher) {
        if (handle != 0L) {
            LlamaLib.nativeFree(handle)
            handle = 0L
        }
        // JP: 上位で複数回呼ばれても安全 / EN: idempotent
    }

    /**
     * JP: AutoCloseable 実装。try-with-resources 的に明示解放が可能です。
     * EN: AutoCloseable implementation for deterministic cleanup.
     */
    override fun close() {
        runBlocking { release() }
        // JP: Dispatcher を最後に停止 / EN: finally shut down the dispatcher
        dispatcher.close()
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------
    companion object {
        /**
         * JP: モデルファイルパスを直接指定してコンテキストを作成します。
         * EN: Create a context from a model file path.
         */
        fun createContextFromFile(modelPath: String, nCtx: Int = 2048): LlamaContext {
            val ptr = LlamaLib.nativeLoadModel(modelPath, nCtx)
            require(ptr != 0L) { "Couldn't create context with path $modelPath" }
            return LlamaContext(ptr)
        }

        /**
         * JP: InputStream を一時ファイルに保存してからロードします。
         * EN: Persist an InputStream to a temp file and then load it.
         */
        fun createContextFromInputStream(
            stream: InputStream,
            cacheDir: File,
            nCtx: Int = 2048,
            fileName: String = "llama_model.gguf",
            overwrite: Boolean = false
        ): LlamaContext {
            require(cacheDir.exists() || cacheDir.mkdirs()) {
                "Failed to create cache directory: ${cacheDir.absolutePath}"
            }
            val file = File(cacheDir, fileName)

            if (overwrite || !file.exists() || file.length() == 0L) {
                stream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                // 任意: file.setReadable(true, /*ownerOnly=*/false)
            }

            return createContextFromFile(file.absolutePath, nCtx)
        }

        /**
         * JP: Asset からロード（Context を渡す版：安全）。内部で cacheDir に展開します。
         * EN: Load from an asset (safe version). Extracts the asset into cacheDir.
         */
        fun createContextFromAsset(
            context: Context,
            assetPath: String,
            nCtx: Int = 2048,
            overwrite: Boolean = false
        ): LlamaContext {
            val fileName = assetPath.substringAfterLast('/')
            context.assets.open(assetPath).use { input ->
                return createContextFromInputStream(
                    stream = input,
                    cacheDir = context.cacheDir,
                    nCtx = nCtx,
                    fileName = fileName,
                    overwrite = overwrite
                )
            }
        }

        /**
         * JP: AssetManager を直接受け取る版（Context がない場合用）。
         *     cacheDir は必須。推奨は Context 版の使用です。
         * EN: Variant that accepts AssetManager directly (when Context is not available).
         *     cacheDir is required; prefer the Context-based overload.
         */
        fun createContextFromAsset(
            assetManager: AssetManager,
            assetPath: String,
            cacheDir: File,
            nCtx: Int = 2048,
            overwrite: Boolean = false
        ): LlamaContext {
            require(cacheDir.exists() || cacheDir.mkdirs()) {
                "Failed to create cache directory: ${cacheDir.absolutePath}"
            }
            val fileName = assetPath.substringAfterLast('/')
            assetManager.open(assetPath).use { input ->
                return createContextFromInputStream(
                    stream = input,
                    cacheDir = cacheDir,
                    nCtx = nCtx,
                    fileName = fileName,
                    overwrite = overwrite
                )
            }
        }
    }
}

/**
 * =============================================================================
 * Native loader & JNI facade
 * =============================================================================
 * JP: C++ 側の JNI クラス名は `com.negi.nativelib.Llama`。
 *     ここではそのインスタンスを生成して委譲します。
 *
 * EN: The native JNI class on C++ side is `com.negi.nativelib.Llama`.
 *     We hold a single instance and delegate all calls to it.
 */
private object LlamaLib {
    init {
        // JP: CMake で生成したブリッジ .so をロード（依存の "llama" は連鎖ロード）。
        // EN: Load the bridge .so built by CMake (which links against "llama").
        System.loadLibrary("llama_bridge")
        Log.d(LOG_TAG, "Loaded native library: llama_bridge")
    }

    // JNI target (C++ side has instance methods)
    private val jni = Llama()

    fun nativeLoadModel(modelPath: String, nCtx: Int): Long =
        jni.nativeLoadModel(modelPath, nCtx)

    fun nativeCompletion(
        handle: Long,
        prompt: String,
        nThreads: Int,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        seed: Int
    ): String = jni.nativeCompletion(handle, prompt, nThreads, maxTokens, temp, topP, seed)

    fun nativeFree(handle: Long) = jni.nativeFree(handle)
}

/**
 * =============================================================================
 * JNI binding class
 * =============================================================================
 * JP: C++ 側のシンボルは `Java_com_negi_nativelib_Llama_native*`（インスタンスメソッド）
 * EN: C++ symbols follow `Java_com_negi_nativelib_Llama_native*` (instance methods)
 *
 * ProGuard/R8:
 * -keep class com.negi.nativelib.Llama { *; }
 */
@Keep
internal class Llama {
    external fun nativeLoadModel(modelPath: String, nCtx: Int): Long
    external fun nativeCompletion(
        handle: Long,
        prompt: String,
        nThreads: Int,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        seed: Int
    ): String
    external fun nativeFree(handle: Long)
}
