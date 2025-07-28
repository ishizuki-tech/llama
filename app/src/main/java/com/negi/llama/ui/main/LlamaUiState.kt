// LlamaUiState.kt
package com.negi.llama.ui.main

/**
 * EN: UI-facing state container for the screen.
 *     - `isModelLoading`: true while model/context is being prepared
 *     - `ready`:         model/context is ready to accept prompts
 *     - `isGenerating`:  a completion request is running
 *     - `prompt`:        current input prompt
 *     - `output`:        last completion result
 *     - `error`:         user-visible error message (null if none)
 *
 * JP: 画面向けの状態クラス。
 *     - `isModelLoading`: モデル／コンテキスト準備中フラグ
 *     - `ready`:         プロンプトを受け付けられる状態
 *     - `isGenerating`:  生成処理の実行中フラグ
 *     - `prompt`:        現在の入力プロンプト
 *     - `output`:        直近の生成結果
 *     - `error`:         ユーザー向けエラーメッセージ（なければ null）
 */
data class LlamaUiState(
    val isModelLoading: Boolean = false,
    val ready: Boolean = false,
    val isGenerating: Boolean = false,
    val prompt: String = "",
    val output: String = "",
    val error: String? = null
)
