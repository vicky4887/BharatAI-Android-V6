#!/usr/bin/env bash
# Source this file. Select a *full JDK* compatible with Gradle 8.7 + AGP 8.6.1.
# Supports aarch64 MSI EdgeXpert and regular Linux installations.
# Requires Bash; no downloads or system modifications.

_bharatai_java_major() {
  local output
  output="$("$1/bin/java" -version 2>&1)" || return 1
  sed -nE 's/.*version "(1\.)?([0-9]+)([.\"+ -].*)?".*/\2/p' <<< "$output" | head -1
}
_bharatai_jdk_acceptable() {
  local home="$1" major
  [[ -x "$home/bin/java" && -x "$home/bin/javac" ]] || return 1
  major="$(_bharatai_java_major "$home")"
  [[ "$major" == 17 || "$major" == 21 ]]
}
_bharatai_jdk_major() { _bharatai_java_major "$1"; }

bharatai_select_jdk() {
  local home candidate resolved major
  # An explicit override takes priority and MUST be valid rather than silently falling back.
  if [[ -n "${BHARATAI_JDK:-}" ]]; then
    home="${BHARATAI_JDK%/}"
    if ! _bharatai_jdk_acceptable "$home"; then
      echo "ERROR: BHARATAI_JDK='$home' is not a full JDK 17 or 21." >&2
      echo 'Set BHARATAI_JDK to a JDK 17 directory containing bin/java and bin/javac.' >&2
      return 1
    fi
    export JAVA_HOME="$home"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "BharatAI Gradle JDK: $JAVA_HOME (Java $(_bharatai_jdk_major "$JAVA_HOME"))"
    return 0
  fi
  local -a candidates=()
  # Some users have stale JAVA_HOME=Java8. Never take it unless supported.
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  [[ -n "${STUDIO_JDK:-}" ]] && candidates+=("$STUDIO_JDK")
  [[ -n "${ANDROID_STUDIO_JDK:-}" ]] && candidates+=("$ANDROID_STUDIO_JDK")
  candidates+=(
    /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/java-17-openjdk-amd64
    /usr/lib/jvm/java-21-openjdk-arm64 /usr/lib/jvm/java-21-openjdk-amd64
    "$HOME/android-studio/jbr" "$HOME/android-studio/jre"
    /opt/android-studio/jbr /opt/android-studio/jre
    "$HOME/.local/share/JetBrains/Toolbox/apps/AndroidStudio/ch-0"/"0"/jbr
  )
  local dir
  for dir in /usr/lib/jvm/* "$HOME"/.jdks/* "$HOME"/.sdkman/candidates/java/* /opt/android-studio*/jbr /opt/android-studio*/jre "$HOME"/android-studio*/jbr; do
    [[ -d "$dir" ]] && candidates+=("$dir")
  done
  if command -v java >/dev/null 2>&1; then
    resolved="$(readlink -f "$(command -v java)")"
    [[ "$resolved" == */bin/java ]] && candidates+=("${resolved%/bin/java}")
  fi
  # Prefer JDK 17, then 21, regardless of order; Gradle 8.7 cannot run on Java 22.
  for major in 17 21; do
    for candidate in "${candidates[@]}"; do
      [[ -n "$candidate" ]] || continue
      home="${candidate%/}"
      if _bharatai_jdk_acceptable "$home" && [[ "$(_bharatai_jdk_major "$home")" == "$major" ]]; then
        export JAVA_HOME="$home"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "BharatAI Gradle JDK: $JAVA_HOME (Java $major)"
        echo 'Gradle 8.7 / Android Gradle Plugin 8.6.1: compatible JVM selected.'
        return 0
      fi
    done
  done
  echo 'ERROR: Could not locate a full JDK 17 or 21 for Gradle.' >&2
  echo 'Gradle was selecting Java 8 (plugin incompatibility) or Java 22 (Gradle 8.7 cannot run on it).' >&2
  echo 'On Ubuntu/DGX OS, install JDK 17: sudo apt update && sudo apt install -y openjdk-17-jdk' >&2
  echo 'Then rerun: bash build_android.sh' >&2
  echo 'Or: BHARATAI_JDK=/absolute/path/to/jdk17 bash build_android.sh' >&2
  return 1
}
