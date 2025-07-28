#!/usr/bin/env bash
set -euo pipefail

# ===== Config ================================================================
MODEL_DIR="${MODEL_DIR:-src/main/assets/models}"
LLAMA_VERSION="${LLAMA_VERSION:-3.2-1B-Instruct}"
REPO="${REPO:-bartowski/Llama-${LLAMA_VERSION}-GGUF}"
BRANCH="${BRANCH:-main}"
NAME_PREFIX="${NAME_PREFIX:-Llama-${LLAMA_VERSION}}"

# 軽い順の候補（必要に応じて変更）
read -r -a QUANT_CANDIDATES <<< "${QUANT_CANDIDATES:-Q4_K_M Q4_K_S Q4_K_L Q5_K_M Q5_K_S Q8_0 Q6_K Q3_K_XL}"

# ===== Helpers ===============================================================
need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "❌ missing: $1" >&2; exit 1; }; }
need_cmd curl

mkdir -p "$MODEL_DIR"
cd "$MODEL_DIR"

echo "📦 MODEL_DIR     : $MODEL_DIR"
echo "🦙 LLAMA_VERSION : $LLAMA_VERSION"
echo "📚 REPO          : $REPO (branch: $BRANCH)"
echo "🔢 QUANT order   : ${QUANT_CANDIDATES[*]}"

# ===== Try candidates in order ===============================================
for q in "${QUANT_CANDIDATES[@]}"; do
  file="${NAME_PREFIX}-${q}.gguf"             # ← ハイフン区切りが正しい
  url="https://huggingface.co/${REPO}/resolve/${BRANCH}/${file}"

  # 既に存在するならそれを採用
  if [[ -f "$file" && -s "$file" ]]; then
    echo "✅ $file already exists. Using it."
    exit 0
  fi

  echo "🔎 Trying candidate: $file"
  tmp="${file}.part"

  # 実ダウンロード（失敗時は次候補へ）
  if [[ -n "${HF_TOKEN:-}" ]]; then
    curl -fL -C - --retry 5 --retry-delay 2 --retry-connrefused \
      -H "Authorization: Bearer ${HF_TOKEN}" \
      -o "$tmp" "$url" || { rm -f "$tmp"; echo "↪️  $q not available, trying next..."; continue; }
  else
    curl -fL -C - --retry 5 --retry-delay 2 --retry-connrefused \
      -o "$tmp" "$url" || { rm -f "$tmp"; echo "↪️  $q not available, trying next..."; continue; }
  fi

  # 完了判定（サイズ>0）
  if [[ -s "$tmp" ]]; then
    mv -f "$tmp" "$file"
    echo "✅ Download complete: $file"
    exit 0
  else
    rm -f "$tmp"
    echo "↪️  Empty download for $q, trying next..."
  fi
done

echo "❌ No candidate quantization could be downloaded from repo: $REPO"
echo "   Try adjusting LLAMA_VERSION / REPO / QUANT_CANDIDATES."
exit 1
