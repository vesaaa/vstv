#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENC="${ROOT}/proprietary/bundle.tar.gz.enc"
DEST="${ROOT}/app/src/main/java/com/vesaa/mytv/defaults"

if [[ ! -f "$ENC" ]]; then
  echo "::error::缺少加密包: $ENC"
  exit 1
fi

if [[ -z "${PROPRIETARY_AGE_PASSPHRASE:-}" ]]; then
  echo "::warning::未配置 PROPRIETARY_AGE_PASSPHRASE，跳过 proprietary 解密；将使用仓库中已提交的 defaults/*.kt。"
  echo "::warning::发布含内置端点解密的正式包前，请在目标仓库的 Actions Secrets 中配置 PROPRIETARY_AGE_PASSPHRASE（与加密 bundle 口令一致）。"
  exit 0
fi

mkdir -p "$DEST"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -pass env:PROPRIETARY_AGE_PASSPHRASE \
  -in "$ENC" -out "$TMP"

set +e
tar -xzf "$TMP" -C "$DEST" --wildcards '*.kt' 2>/dev/null
rc=$?
set -e

if [[ "$rc" -ne 0 ]]; then
  echo "::notice::bundle 中未解压出 *.kt，将使用仓库内的默认 [defaults/AppBuiltinEndpoints.kt]"
else
  echo "已自 bundle 还原 *.kt 至 $DEST"
fi

# 旧版 bundle 可能含 proprietary 包下的文件名，避免与 defaults 目录结构冲突
rm -f "$DEST/ProprietaryUpdate.kt" "$DEST"/*.go 2>/dev/null || true

ls -la "$DEST"
