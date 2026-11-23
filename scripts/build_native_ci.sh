#!/usr/bin/env bash
# Build the actual V6 native variant on a Linux x86_64 machine or GitHub Actions.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -m)" != "x86_64" ]]; then
  echo 'ERROR: The official Android NDK Linux toolchain needs an x86_64 build host.' >&2
  echo 'On your ARM64 EdgeXpert, use the included GitHub Actions workflow instead.' >&2
  exit 2
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo 'ERROR: ANDROID_HOME is missing (set by the GitHub Actions workflow).' >&2
  exit 2
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
  echo 'ERROR: JDK 17 JAVA_HOME not configured.' >&2
  exit 2
fi
if [[ ! -d "$ANDROID_HOME/ndk/27.2.12479018" ]]; then
  echo 'ERROR: Official Android NDK 27.2.12479018 is not installed.' >&2
  exit 2
fi
bash scripts/fetch_llama_source.sh
# The source ZIP omits generated Gradle wrapper files. Bootstrap uses official SHA-verified Gradle 8.7.
if [[ ! -x ./gradlew || ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  bash bootstrap_gradle.sh
fi
# Always build JNI: -PbharataiNative=true. Never silently fall back to OCR-only mode.
./gradlew --no-daemon -Dorg.gradle.java.home="$JAVA_HOME"   -PbharataiNative=true :app:testDebugUnitTest :app:assembleDebug --stacktrace
apk=app/build/outputs/apk/debug/app-debug.apk
test -s "$apk" || { echo "ERROR: APK not generated: $apk" >&2; exit 3; }
unzip -Z1 "$apk" | grep -Fxq 'lib/arm64-v8a/libbharatai_llm.so' || {
  echo 'ERROR: APK did not package native BharatAI V6 JNI. Refusing to label as native.' >&2
  exit 3
}
echo "SUCCESS: native V6 APK compiled: $apk"
