#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
python3 verification/check_project.py
if ! command -v kotlinc >/dev/null; then echo 'kotlinc not found. Install Kotlin compiler or run Android Studio tests.' >&2; exit 1; fi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/com/bharatai/education/core/*.kt verification/CoreSmoke.kt -include-runtime -d "$TMP/test.jar"
java -jar "$TMP/test.jar"
