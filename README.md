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
│   └── LlamaContext.kt        # JNI ラッパー（Kotlin）
```

---

## 🧠 Sample Kotlin Usage / Kotlin 利用例

```kotlin
val llama = LlamaContext.init(context.assets, "models/Llama-3.2-1B-Instruct-Q4_K_M.gguf")

val response = llama.complete("What is the capital of Kenya?")

println(response) // => The capital of Kenya is Nairobi.
```

---

## 📦 Model Suggestions / 推奨モデル

| Model                 | Size    | Min RAM | Notes                    |
| --------------------- | ------- | ------- | ------------------------ |
| LLaMA-3.2 1B Q4\_K\_M | \~770MB | \~2GB   | Good baseline for mobile |
| LLaMA-3.2 3B Q4\_K\_M | \~2.7GB | \~5GB+  | High-end Android only    |

---

## 🔧 ABI Support / ABI サポート

* `armeabi-v7a` – Detects `vfpv4`
* `arm64-v8a` – Detects `fp16`
* `x86` / `x86_64` also supported

Runtime detection ensures optimal native performance.
実行時に ABI に応じた最適なネイティブライブラリを選択します。

---

## 🔒 License / ライセンス

* This wrapper: Apache 2.0
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
Built for offline AI in the real world.
