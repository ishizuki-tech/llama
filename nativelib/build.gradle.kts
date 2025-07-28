plugins {
    // Android ライブラリモジュール用プラグイン
    // Plugin for Android Library module
    alias(libs.plugins.android.library)

    // Kotlin for Android プラグイン
    // Plugin for Kotlin Android support
    alias(libs.plugins.kotlin.android)
}

android {
    // 名前空間（パッケージ名）
    // Application namespace (should match the package name)
    namespace = "com.negi.nativelib"

    // 使用する Android SDK のバージョン
    // Target Android SDK version
    compileSdk = 35

    // 使用する NDK のバージョン（ビルドの再現性・安定性向上）
    // Specify NDK version to ensure reproducible and stable builds
    ndkVersion = "26.3.11579264"

    defaultConfig {
        // 対応する最小 Android バージョン
        // Minimum supported Android version
        minSdk = 23

        // サポートする ABI（CPU アーキテクチャ）
        // Target ABI architectures (start with arm64-v8a for real devices)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // CMake の設定（ネイティブコードビルド）
        // Native build configuration using CMake
        externalNativeBuild {
            cmake {
                // C++ コンパイラフラグ（最適化・例外処理など）
                // Compiler flags for optimization and behavior
                cppFlags += "-O3 -DNDEBUG -fexceptions -frtti -fvisibility=hidden"

                // CMake に渡すビルド引数
                // CMake arguments to control ggml build behavior
                arguments += listOf(
                    "-DGGML_OPENMP=OFF",      // Disable OpenMP for compatibility
                    "-DGGML_BLAS=OFF",        // Disable BLAS
                    "-DGGML_NATIVE=OFF",      // Disable native CPU optimizations
                    "-DGGML_COMPILER_SUPPORTS_FP16_FORMAT_I3E=" // Suppress auto FP16 flags
                )
            }
        }

        // インストルメンテーションテスト用ランナー
        // Test runner for instrumentation tests
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ライブラリ利用者向け Proguard 設定
        // Proguard rules for library consumers
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // Proguard 無効化（ライブラリの場合は通常不要）
            // Disable code shrinking/minification for libraries
            isMinifyEnabled = false

            // Proguard 設定ファイル
            // Proguard configuration files
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // ネイティブのデバッグシンボルを AAR に含めたい場合
            // (Optional) Include native debug symbols in AAR
            // ndk { debugSymbolLevel = DebugSymbolLevel.FULL }
        }

        // debug ビルドには特別な設定は不要
        // No special config needed for debug in library modules
    }

    // CMakeLists.txt の場所と使用する CMake のバージョン
    // Path to CMakeLists.txt and CMake version to use
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Java コンパイルオプション
    // Java compilation settings
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kotlin JVM ターゲットバージョン
    // Kotlin JVM target version
    kotlinOptions {
        jvmTarget = "17"
    }

    // Prefab を利用して AAR に .so を含めたい場合（今回は無効）
    // Enable Prefab for native .so distribution if needed
    // buildFeatures { prefab = true }
}

dependencies {
    // Android 基本ライブラリ
    // Core Android libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // 単体テスト用依存関係
    // Unit test dependencies
    testImplementation(libs.junit)

    // Android UI テスト用依存関係
    // UI test dependencies
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
