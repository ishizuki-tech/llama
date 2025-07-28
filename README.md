# 🦙 Llama.cpp Android Integration | Android 向け Llama.cpp 統合

A minimal, production-ready integration of [llama.cpp](https://github.com/ggerganov/llama.cpp) on Android using JNI and Jetpack Compose.

Jetpack Compose と JNI を用いて、[llama.cpp](https://github.com/ggerganov/llama.cpp) を Android に統合する最小限かつ実用的なテンプレートです。

---

## ✅ Features / 主な特徴

- Native model inference on-device (fully offline)  
  モデルを端末上でローカル実行（完全オフライン動作）

- Built with NDK + CMake (`libllama.so`)  
  NDK + CMake による C++ ビルド（`libllama.so`）

- Kotlin-side wrapper with coroutine support  
  Kotlin からコルーチンで安全に呼び出し

- Automatic CPU threading optimization  
  CPU 構成に応じたスレッド最適化

---

## 🚀 Quick Start / クイックスタート

### 1. Clone the repository / リポジトリをクローン

```bash
git clone git@github.com:ishizuki-tech/llama.git
cd llama
````

### 2. Add model to assets / モデルファイルを追加

Place a quantized `.gguf` model (e.g., `Llama-3.2-1B-Instruct-Q4_K_M.gguf`) into:

```
app/src/main/assets/models/
```

> ⚠️ GitHub では 100MB 超のファイルを直接 push できません。
> Use [Git LFS](https://git-lfs.github.com) or manage manually.
> Git LFS または手動で管理してください。

### 3. Open in Android Studio / Android Studio で開く

* Compile SDK: 35+
* NDK: 26.3+
* Run on device with **2GB+ RAM**
  端末は RAM 2GB 以上推奨

---

## 📂 Project Structure / プロジェクト構成

```
.
├── app/                        # Jetpack Compose UI
│   └── src/main/assets/models/ # モデル格納ディレクトリ
├── nativelib/                 # JNI & ネイティブコード
│   ├── src/main/cpp/llama.cpp # llama.cpp ソース
│   └── src/main/java/com/negi/nativeLib/LlamaContext.kt        # JNI ラッパー（Kotlin）
```

---

## 🧠 Sample Kotlin Usage / Kotlin 利用例

```kotlin
val llama = LlamaContext.init(context.assets, "models/Llama-3.2-1B-Instruct-Q4_K_M.gguf")

val response = llama.complete("What is the capital of Kenya?")

println(response) // => The capital of Kenya is Nairobi.
```

---

## 📘 Model Details: `Llama-3.2-1B-Instruct-Q4_K_M.gguf`

### 🇬🇧 English

`Llama-3.2-1B-Instruct-Q4_K_M.gguf` is a quantized version of Meta's LLaMA 3.2 model, specially tuned for instruction-following tasks. It’s optimized for on-device use and compatible with `llama.cpp`.

| Attribute        | Description                            |
| ---------------- | -------------------------------------- |
| Model            | LLaMA 3.2 (Meta)                       |
| Size             | \~770MB                                |
| Parameters       | 1 Billion (1B)                         |
| Format           | `.gguf` (next-gen GGML format)         |
| Tuning           | Instruct (single-turn prompt-response) |
| Quantization     | Q4\_K\_M (4-bit, memory-efficient)     |
| Best for         | Mobile / offline apps                  |
| Language Support | English (primary), basic multilingual  |

**Advantages:**

* Lightweight enough for mobile inference
* Fast response with minimal memory usage
* Suitable for offline prompt completion

**Notes:**

* Do not commit this model to GitHub directly (use Git LFS or manual download)
* You can find this model at: [Hugging Face](https://huggingface.co/TheBloke/LLaMA-3.2-1B-Instruct-GGUF)

---

### 🇯🇵 日本語

`Llama-3.2-1B-Instruct-Q4_K_M.gguf` は、Meta 社の LLaMA 3.2 モデルをベースにした、**命令応答（Instruct）向けの量子化モデル**です。軽量かつオフライン動作に最適化されており、`llama.cpp` での利用を前提に設計されています。

| 項目     | 内容                       |
| ------ | ------------------------ |
| モデル    | LLaMA 3.2（Meta）          |
| サイズ    | 約770MB                   |
| パラメータ数 | 約10億（1B）                 |
| フォーマット | `.gguf`（次世代 GGML フォーマット） |
| チューニング | Instruct（命令応答）           |
| 量子化方式  | Q4\_K\_M（4ビット量子化・省メモリ）   |
| 推奨用途   | モバイル / オフライン利用           |
| 言語対応   | 主に英語、一部多言語対応あり           |

**主な利点:**

* モバイルでも実行できる軽量サイズ
* メモリ消費が少なく、推論が高速
* 単一ターンの文章生成に適している

**注意点:**

* GitHub に直接コミットしないでください（Git LFS または手動配置を推奨）
* モデル配布元：[Hugging Face](https://huggingface.co/TheBloke/LLaMA-3.2-1B-Instruct-GGUF)

---

## 📦 Model Suggestions / 推奨モデル

| Model                 | Size    | Min RAM | Notes                    |
| --------------------- | ------- | ------- | ------------------------ |
| LLaMA-3.2 1B Q4\_K\_M | \~770MB | \~2GB   | Good baseline for mobile |
| LLaMA-3.2 3B Q4\_K\_M | \~2.7GB | \~5GB+  | High-end Android only    |

---

## 🔧 ABI Support / ABI サポート

* `arm64-v8a` – Detects `fp16`

Runtime detection ensures optimal native performance.
実行時に ABI に応じた最適なネイティブライブラリを選択します。

---

## 🔒 License / ライセンス

* This wrapper: MIT License
* llama.cpp: MIT License

---

## 🙏 Credits / 謝辞

* [ggerganov/llama.cpp](https://github.com/ggerganov/llama.cpp)
* Android NDK, Jetpack Compose, Kotlin team
* Optimized and maintained by [Ishizuki Tech LLC](https://ishizuki.tech)

---

## 📬 Contact / お問い合わせ

Developed by **Ishizuki Tech LLC**
Email: [ishizuki.tech@gmail.com](mailto:ishizuki.tech@gmail.com)
