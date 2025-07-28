// MainActivity.kt
package com.negi.llama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.negi.llama.ui.main.LlamaScreen
import com.negi.llama.ui.theme.LlamaTheme

/**
 * EN: The entry Activity for the application.
 *     - Enables edge-to-edge to draw behind system bars (status/navigation).
 *     - Sets the app-wide Compose theme.
 *     - Hosts the top-level screen (LlamaScreen).
 *
 * JP: アプリのエントリ用 Activity です。
 *     - エッジ・トゥ・エッジ描画を有効化（ステータス／ナビゲーションバーの背後まで描画）。
 *     - アプリ全体の Compose テーマを適用。
 *     - トップレベル画面（LlamaScreen）を表示します。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // EN: Allow the app content to extend under system bars.
        // JP: アプリのコンテンツがシステムバーの下まで広がるように設定します。
        enableEdgeToEdge()

        // EN: Set the Compose content tree.
        // JP: Compose のコンテンツツリーをセットします。
        setContent {
            /**
             * EN: App theme wrapper. Typically provides Material3 color/typography/shape.
             *     If your LlamaTheme already wraps MaterialTheme, you do not need an extra MaterialTheme here.
             *
             * JP: アプリ共通のテーマラッパー。通常は Material3 の色／タイポグラフィ／形状を提供します。
             *     すでに LlamaTheme 内で MaterialTheme を適用している場合、ここで別途 MaterialTheme を重ねる必要はありません。
             */
            LlamaTheme {
                // EN: Render the main screen of the app.
                // JP: アプリのメイン画面を表示します。
                LlamaScreen()
            }
        }
    }
}
