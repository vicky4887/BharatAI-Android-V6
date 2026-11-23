#!/usr/bin/env bash
# One-command Android build for JDK 17 + API35 SDK, including Linux ARM64 AAPT2 override.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/scripts/select_build_jdk.sh"
bharatai_select_jdk
cd "$ROOT"
echo 'Checking / installing Android SDK prerequisites...'
bash "$ROOT/setup_android_sdk.sh" --install
# Read only this setup script's generated environment entries.
[[ -f "$ROOT/.bharatai-sdk.env" ]] || { echo 'SDK environment file missing' >&2; exit 1; }
source "$ROOT/.bharatai-sdk.env"
export ANDROID_HOME="$BHARATAI_SDK_DIR" ANDROID_SDK_ROOT="$BHARATAI_SDK_DIR"
extra=()
if [[ -f "$ROOT/.bharatai-arm64-aapt2.env" ]]; then
  source "$ROOT/.bharatai-arm64-aapt2.env"
  extra+=("-Pandroid.aapt2FromMavenOverride=$BHARATAI_AAPT2")
fi
if [[ ! -x "$ROOT/gradlew" || ! -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]]; then
  echo 'Gradle wrapper missing: bootstrapping Gradle 8.7...'
  bash "$ROOT/bootstrap_gradle.sh"
fi
printf '\nGradle JVM verification:\n'
"$ROOT/gradlew" -Dorg.gradle.java.home="$JAVA_HOME" --no-daemon --version
printf '\nBuilding BharatAI Android app:\n'
"$ROOT/gradlew" -Dorg.gradle.java.home="$JAVA_HOME" --no-daemon \
  "${extra[@]}" :app:testDebugUnitTest :app:assembleDebug "$@"
printf '\nBUILD COMPLETE. APK: %s\n' "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
