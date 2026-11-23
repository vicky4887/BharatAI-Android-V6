#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/scripts/select_build_jdk.sh"
bharatai_select_jdk
echo 'Selected Java runtime:'
"$JAVA_HOME/bin/java" -version
echo 'Selected Java compiler:'
"$JAVA_HOME/bin/javac" -version
echo "Effective JAVA_HOME=$JAVA_HOME"
