#!/usr/bin/env bash
# Pin upstream API to a real release. Do not check out moving HEAD/master.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/llama.cpp"
TAG='v0.6.0'
if [[ -d "$DEST/.git" ]]; then
    actual="$(git -C "$DEST" describe --tags --exact-match 2>/dev/null || true)"
    if [[ "$actual" != "$TAG" ]]; then
        echo "ERROR: Existing llama.cpp checkout ($actual) is not pinned to $TAG: $DEST" >&2
        echo "Inspect this folder and remove/relocate it manually before retrying." >&2
        exit 2
    fi
elif [[ -e "$DEST" ]]; then
    echo "ERROR: $DEST exists but is not a git checkout; won't overwrite." >&2
    exit 2
else
    git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git "$DEST"
fi
printf 'Using %s at commit %s\n' "$TAG" "$(git -C "$DEST" rev-parse HEAD)"
