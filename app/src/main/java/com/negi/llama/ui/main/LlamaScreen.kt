// LlamaScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.negi.llama.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import android.content.ClipData
import android.util.Log
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.toClipEntry

private const val LOG_TAG = "LlamaScreen"

/**
 * EN: Top-level screen for running on-device LLM completions.
 *     - Loads the model once on first composition
 *     - Lets the user enter a prompt and tweak decoding params
 *     - Shows generation result and basic actions (copy / cancel)
 *
 * JP: 端末内 LLM 推論用のトップレベル画面です。
 *     - 初回コンポーズ時にモデルを一度だけロード
 *     - プロンプト入力とデコーディングパラメータの調整が可能
 *     - 生成結果の表示と基本操作（コピー／停止）を提供します
 */
@Composable
fun LlamaScreen(
    vm: LlamaViewModel = viewModel()
) {
    // EN: Load model only once when the screen first appears.
    // JP: 画面初期表示時に一度だけモデルをロードします。
    LaunchedEffect(Unit) {
        Log.d(LOG_TAG, "LaunchedEffect is called...")
        vm.loadModelIfNeeded()
        Log.d(LOG_TAG, "LaunchedEffect is called... after loadModelIfNeeded")
    }

    // EN: Observe UI state with lifecycle awareness.
    // JP: ライフサイクル対応で UI 状態を監視します。
    val state by vm.state.collectAsStateWithLifecycle()

    // EN: UI parameters saved across recompositions / rotations.
    // JP: 再コンポーズや回転でも保持される UI パラメータ。
    var temp by rememberSaveable { mutableFloatStateOf(0.8f) }
    var topP by rememberSaveable { mutableFloatStateOf(0.95f) }
    var maxTokens by rememberSaveable { mutableFloatStateOf(128f) }
    var seedText by rememberSaveable { mutableStateOf("") }

    val snackBarHostState = remember { SnackbarHostState() }
    val outputScroll = rememberScrollState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // EN: Surface any error via SnackBar.
    // JP: エラーは SnackBar で通知します。
    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackBarHostState.showSnackbar(message = msg)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Llama (on-device)") }) },
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()               // EN: Avoid IME; JP: キーボード回避
                .navigationBarsPadding()    // EN: Avoid system bars; JP: システムバー回避
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // EN: Model loading indicator.
            // JP: モデル読み込み中の表示。
            if (state.isModelLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("モデルを読み込み中…")
            }

            // EN: Main UI is shown when model is ready.
            // JP: モデルが準備できたらメイン UI を表示。
            if (state.ready) {
                // === Prompt ===
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = vm::updatePrompt,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("プロンプト / Prompt") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            vm.generate(
                                maxTokens = maxTokens.toInt(),
                                temperature = temp,
                                topP = topP,
                                seed = seedText.toIntOrNull() ?: -1
                            )
                        }
                    )
                )

                // === Parameters ===
                ParamSlider(
                    title = "temperature",
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = 0.0f..1.5f,
                    steps = 14
                )

                ParamSlider(
                    title = "topP",
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.1f..1.0f,
                    steps = 9
                )

                ParamSlider(
                    title = "maxTokens: ${maxTokens.toInt()}",
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    valueRange = 16f..512f,
                    steps = 31
                )

                OutlinedTextField(
                    value = seedText,
                    onValueChange = { seedText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("seed（省略可 / -1 でランダム）") },
                    singleLine = true
                )

                // === Actions ===
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            vm.generate(
                                maxTokens = maxTokens.toInt(),
                                temperature = temp,
                                topP = topP,
                                seed = seedText.toIntOrNull() ?: -1
                            )
                        },
                        enabled = !state.isGenerating && state.prompt.isNotBlank()
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("生成中… / Generating…")
                        } else {
                            Text("生成 / Generate")
                        }
                    }

                    // EN: Stop button cancels the current coroutine job.
                    // JP: 停止ボタンで現在のコルーチン処理をキャンセルします。
                    OutlinedButton(
                        onClick = { vm.cancelGeneration() },
                        enabled = state.isGenerating
                    ) { Text("停止 / Stop") }
                }

                // === Output ===
                if (state.output.isNotBlank()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("出力 / Output", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = {
                                // EN: LocalClipboard uses suspend APIs; call within a coroutine.
                                // JP: LocalClipboard の API は suspend のため、コルーチン内で呼びます。
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText("text", state.output).toClipEntry()
                                    )
                                }
                            }
                        ) { Text("コピー / Copy") }
                    }
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(outputScroll)
                        ) {
                            Text(state.output)
                        }
                    }
                }
            }

            // EN: Retry affordance when model is not ready and an error occurred.
            // JP: モデル未準備かつエラー時に再試行ボタンを表示します。
            if (!state.ready && !state.isModelLoading && state.error != null) {
                OutlinedButton(onClick = { vm.loadModelIfNeeded() }) { Text("再試行 / Retry") }
            }
        }
    }
}

/**
 * EN: Labeled slider used to adjust float parameters (temperature/topP/maxTokens).
 *     - `steps` specifies the number of discrete tick marks between min and max.
 *
 * JP: 浮動小数値パラメータ（temperature/topP/maxTokens）を調整するラベル付きスライダーです。
 *     - `steps` は最小値と最大値の間に設ける中間刻み数を指定します。
 */
@Composable
private fun ParamSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column {
        Text("$title: ${"%.2f".format(value)}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
