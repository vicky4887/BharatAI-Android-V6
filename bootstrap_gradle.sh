#!/usr/bin/env bash
# Bootstrap the official Gradle 8.7 wrapper without first configuring Android plugins.
# Gradle 8.7 must be launched on a full JDK 17/21 (NOT Java 8 or 22).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/scripts/select_build_jdk.sh"
bharatai_select_jdk
cd "$ROOT"
DIST_DIR="$ROOT/.gradle-dist"
ZIP="$DIST_DIR/gradle-8.7-bin.zip"
HASH="544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d"
mkdir -p "$DIST_DIR"
if [[ ! -x "$DIST_DIR/gradle-8.7/bin/gradle" ]]; then
  if [[ ! -f "$ZIP" ]]; then
    echo 'Downloading official Gradle 8.7 (internet needed on development computer)...'
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --connect-timeout 15 -o "$ZIP.part" https://services.gradle.org/distributions/gradle-8.7-bin.zip
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP.part" https://services.gradle.org/distributions/gradle-8.7-bin.zip
    else
      echo 'ERROR: curl or wget required to download Gradle.' >&2; exit 1
    fi
    mv "$ZIP.part" "$ZIP"
  fi
  echo "$HASH  $ZIP" | sha256sum -c -
  unzip -q -o "$ZIP" -d "$DIST_DIR"
fi
if [[ ! -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" || ! -x "$ROOT/gradlew" ]]; then
  echo 'Generating Gradle wrapper from a MINIMAL temporary build (no AGP resolution)...'
  TMP="$DIST_DIR/wrapper-bootstrap"
  mkdir -p "$TMP"
  printf "rootProject.name = 'BharatAIWrapperBootstrap'\n" > "$TMP/settings.gradle"
  printf "// No Android plugins here: bootstrap is independent from app configuration.\n" > "$TMP/build.gradle"
  "$DIST_DIR/gradle-8.7/bin/gradle" -p "$TMP" \
    -Dorg.gradle.java.home="$JAVA_HOME" --no-daemon \
    wrapper --gradle-version 8.7 --distribution-type bin
  install -m 755 "$TMP/gradlew" "$ROOT/gradlew"
  cp "$TMP/gradlew.bat" "$ROOT/gradlew.bat"
  mkdir -p "$ROOT/gradle/wrapper"
  cp "$TMP/gradle/wrapper/gradle-wrapper.jar" "$ROOT/gradle/wrapper/gradle-wrapper.jar"
  # Retain the existing checked-in distribution URL and checksum.
fi
if [[ ! -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]]; then
  echo 'ERROR: Gradle wrapper JAR missing after bootstrap.' >&2; exit 1
fi
echo 'Official Gradle 8.7 wrapper ready. Next: bash build_android.sh'
