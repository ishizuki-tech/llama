cmake_minimum_required(VERSION 3.22)
project(nativelib LANGUAGES C CXX)

# =============================================================================
# JP: 共通設定（C/C++ 規格、PIC、可視性）
# EN: Common settings (language standards, PIC, symbol visibility)
# =============================================================================
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# JP: 共有ライブラリ用に Position Independent Code を有効化
# EN: Enable Position Independent Code for shared libraries
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# JP: 不要なシンボルを隠して .so を軽量化（JNI エクスポートは名前規約で可視のまま）
# EN: Hide non-JNI symbols to reduce .so size (JNI exports remain visible via naming)
set(C_VISIBILITY_PRESET hidden)
set(CXX_VISIBILITY_PRESET hidden)
set(VISIBILITY_INLINES_HIDDEN YES)

# =============================================================================
# JP: llama.cpp のビルド構成（例：examples/tests を無効化してビルドを軽量化）
# EN: llama.cpp build knobs (disable examples/tests to keep build light)
# =============================================================================
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS     OFF CACHE BOOL "" FORCE)

# ここで GPU 関連などを無効化/有効化（必要に応じて）
# Disable/enable GPU-related backends as needed, e.g.:
# set(GGML_CUDA OFF CACHE BOOL "" FORCE)
# set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
# set(GGML_METAL OFF CACHE BOOL "" FORCE)  # Android では通常 OFF

# =============================================================================
# JP: llama.cpp をサブディレクトリとして追加
# EN: Bring in llama.cpp as a subdirectory
# =============================================================================
# 例: <module>/src/main/cpp/llama.cpp に clone/submodule してある前提
add_subdirectory(${CMAKE_SOURCE_DIR}/llama.cpp ${CMAKE_BINARY_DIR}/llama_build)

# =============================================================================
# JP: JNI ブリッジ（この共有ライブラリに JNI 関数が入る）
# EN: JNI bridge (this shared library contains the JNI functions)
# =============================================================================
add_library(llama_bridge SHARED
        llama_bridge.cpp
)

# JP: ヘッダー検索パス（llama.h, ggml ヘッダーなど）
# EN: Header search paths (llama.h, ggml headers, etc.)
target_include_directories(llama_bridge PRIVATE
        ${CMAKE_SOURCE_DIR}/llama.cpp
        ${CMAKE_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
)

# JP: 最適化・警告などは必要に応じて
# EN: Add your compile options as needed (optimization, warnings, etc.)
# target_compile_options(llama_bridge PRIVATE -O3)

# =============================================================================
# JP: リンク設定
# EN: Linking
# =============================================================================
target_link_libraries(llama_bridge PRIVATE
        llama     # JP: llama.cpp で生成されるコアライブラリ / EN: core library from llama.cpp
        log       # android log
        android   # AAssetManager 等を使う場合に備えて
)

# JP: armeabi-v7a では 64bit アトミック用途に libatomic が必要になる場合あり
# EN: On armeabi-v7a, -latomic may be required for 64-bit atomics
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_link_libraries(llama_bridge PRIVATE atomic)
endif()

# JP: 未解決シンボルを検出（早期にリンクエラーに）
# EN: Fail on unresolved symbols early
target_link_options(llama_bridge PRIVATE -Wl,--no-undefined)

# =============================================================================
# JP: 注意：Java/Kotlin からは "llama_bridge" をロードしてください。
#     例) System.loadLibrary("llama_bridge")
#     "llama" は依存として自動ロードされます（Gradle によるパッケージングに含まれます）。
#
# EN: IMPORTANT: From Java/Kotlin, call System.loadLibrary("llama_bridge").
#     The "llama" library will be auto-loaded as a dependency and packaged by Gradle.
# =============================================================================
