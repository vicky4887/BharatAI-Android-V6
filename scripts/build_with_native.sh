#!/usr/bin/env bash
# Native V6 build entry point for compatible x86_64 build hosts.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -m)" != "x86_64" ]]; then
  echo 'Native compilation is not supported on standard ARM64 Linux NDK hosts.' >&2
  echo 'Use the included GitHub Actions x86_64 runner: see BUILD_NATIVE_ON_EDGEXPERT.md' >&2
  exit 2
fi
bash scripts/build_native_ci.sh
