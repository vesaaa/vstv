#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENC="${ROOT}/proprietary/bundle.tar.gz.enc"
DEST="${ROOT}/app/src/main/java/com/vesaa/mytv/proprietary"

if [[ ! -f "$ENC" ]]; then
  echo "::error::缺少加密包: $ENC"
  exit 1
fi

if [[ -z "${PROPRIETARY_AGE_PASSPHRASE:-}" ]]; then
  echo "::error::未配置 Secret PROPRIETARY_AGE_PASSPHRASE，无法解密专有源码"
  exit 1
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
  echo "::notice::bundle 中未解压出 *.kt（例如仍为仅含 .go 的旧包），将使用仓库内已提交的 proprietary 源码"
else
  echo "已从 bundle 还原 *.kt 至 $DEST"
fi

ls -la "$DEST"
