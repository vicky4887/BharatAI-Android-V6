#!/usr/bin/env bash
# BharatAI SDK setup for Ubuntu/DGX OS aarch64 and conventional Linux.
# Downloads Google SDK components. Third-party ARM64 tools require explicit opt-in; never uses sudo or apt.
# Requires the user to review/accept Android SDK licenses interactively.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/scripts/select_build_jdk.sh"
bharatai_select_jdk
MODE="${1:---install}"
if [[ "$MODE" != '--install' && "$MODE" != '--check' ]]; then
  echo "Usage: bash setup_android_sdk.sh [--install|--check]" >&2
  exit 2
fi

fail() { echo "ERROR: $*" >&2; exit 1; }
warn() { echo "NOTICE: $*" >&2; }
resolve_sdk() {
  local s
  if [[ -n "${BHARATAI_SDK:-}" ]]; then printf '%s\n' "$BHARATAI_SDK"; return; fi
  # Preserve a previously configured project SDK, unless an explicit ANDROID_HOME was given.
  if [[ -n "${ANDROID_HOME:-}" ]]; then printf '%s\n' "$ANDROID_HOME"; return; fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then printf '%s\n' "$ANDROID_SDK_ROOT"; return; fi
  if [[ -f "$ROOT/local.properties" ]]; then
    s="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tail -1)"
    if [[ -n "$s" ]]; then printf '%s\n' "$s"; return; fi
  fi
  for s in "$HOME/Android/Sdk" "$HOME/android-sdk" /opt/android-sdk /usr/lib/android-sdk; do
    if [[ -f "$s/platforms/android-35/android.jar" ]]; then printf '%s\n' "$s"; return; fi
  done
  printf '%s\n' "$HOME/Android/Sdk"
}
SDK="$(resolve_sdk)"
[[ "$SDK" = /* ]] || fail "Android SDK path must be absolute: '$SDK'"
# Avoid creating anything on a read-only system path if the user selected it.
if [[ "$MODE" == '--install' ]]; then
  mkdir -p "$SDK" || fail "Cannot create SDK at '$SDK'. Set BHARATAI_SDK to a writable location."
  SDK="$(cd "$SDK" && pwd -P)"
fi
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
ARCH="${BHARATAI_TEST_ARCH:-$(uname -m)}"

write_local_properties() {
  python3 - "$ROOT/local.properties" "$SDK" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
sdk = sys.argv[2]
lines = path.read_text(encoding='utf-8').splitlines() if path.exists() else []
lines = [x for x in lines if not x.startswith('sdk.dir=')]
lines.append('sdk.dir=' + sdk.replace('\\', '\\\\').replace(':', '\\:').replace(' ', '\\ '))
path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
PY
}

install_cmdline_tools() {
  local target="$SDK/cmdline-tools/latest" url zip tmp sha
  if [[ -x "$target/bin/sdkmanager" ]]; then return; fi
  [[ "$MODE" == '--install' ]] || fail "SDK platform missing and sdkmanager not installed at $target/bin/sdkmanager"
  url='https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip'
  sha='2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258'
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  zip="$tmp/commandline-tools.zip"
  echo "Downloading verified Android command-line tools from Google (one-time)..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --connect-timeout 20 --output "$zip" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget --tries=3 -O "$zip" "$url"
  else
    fail 'curl or wget required to download official Android SDK command-line tools.'
  fi
  printf '%s  %s\n' "$sha" "$zip" | sha256sum --check --status || fail 'Command-line tools SHA256 mismatch; download NOT installed.'
  unzip -q "$zip" -d "$tmp/extracted"
  [[ -x "$tmp/extracted/cmdline-tools/bin/sdkmanager" ]] || fail 'Unexpected Google SDK command-line tools archive structure.'
  mkdir -p "$SDK/cmdline-tools"
  [[ ! -e "$target" ]] || fail "Incomplete command-line tools already installed at '$target'. Inspect/remove only that incomplete directory and retry."
  mv "$tmp/extracted/cmdline-tools" "$target"
  rm -rf "$tmp"
  trap - EXIT
  echo "Installed official sdkmanager: $target/bin/sdkmanager"
}

# Check candidates without allowing a hung AAPT2 to keep pipes or descendants
# open indefinitely. Python starts each candidate in a new session, writes output
# to DEVNULL (never a pipe), and kills the WHOLE process group on timeout.
# Also reject garbage before trying to execute it and check ELF host architecture.
probe_aapt2() {
  local candidate="$1"
  python3 - "$candidate" "$ARCH" <<'PY_AAPT2'
import os
import signal
import subprocess
import sys
from pathlib import Path

exe = Path(sys.argv[1])
arch = sys.argv[2]
try:
    with exe.open('rb') as f:
        hdr = f.read(20)
except OSError:
    sys.exit(1)

if hdr.startswith(b'\x7fELF'):
    # ELF e_machine lives at bytes 18:20; e_ident[5] is the byte order.
    byteorder = 'little' if hdr[5] == 1 else 'big'
    machine = int.from_bytes(hdr[18:20], byteorder)
    wanted = 183 if arch in ('aarch64', 'arm64') else (62 if arch in ('x86_64', 'amd64') else None)
    if wanted is not None and machine != wanted:
        sys.exit(1)
elif not hdr.startswith(b'#!'):
    # The installer tests use a shell wrapper; an arbitrary executable text blob
    # is not a usable AAPT2 and must never be executed.
    sys.exit(1)

try:
    proc = subprocess.Popen(
        [str(exe), 'version'], stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
except (OSError, ValueError):
    sys.exit(1)

try:
    ret = proc.wait(timeout=2)
except subprocess.TimeoutExpired:
    try:
        os.killpg(proc.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    try:
        proc.wait(timeout=2)
    except subprocess.TimeoutExpired:
        pass
    sys.exit(1)
sys.exit(0 if ret == 0 else 1)
PY_AAPT2
}

find_native_aapt2() {
  local p
  local -a candidates=()
  if [[ -n "${BHARATAI_AAPT2:-}" ]]; then
    candidates=("$BHARATAI_AAPT2")
  else
    candidates=("$SDK/build-tools/35.0.1/aapt2" /usr/lib/android-sdk/build-tools/debian/aapt2 /usr/bin/aapt2)
  fi
  for p in "${candidates[@]}"; do
    [[ -n "$p" && -x "$p" ]] || continue
    if probe_aapt2 "$p"; then
      printf '%s\n' "$p"
      return 0
    fi
    if [[ -n "${BHARATAI_AAPT2:-}" ]]; then
      echo "ERROR: explicitly selected BHARATAI_AAPT2='$p' failed a bounded architecture/version probe." >&2
      return 1
    fi
  done
  return 1
}

if [[ "$MODE" == '--install' ]]; then
  if [[ ! -f "$SDK/platforms/android-35/android.jar" || ! -f "$SDK/build-tools/35.0.1/source.properties" ]]; then
    install_cmdline_tools
    echo 'Android SDK licenses require your review and acceptance. They are not accepted automatically.'
    if [[ ! -t 0 ]]; then
      fail 'Interactive terminal required to review SDK licenses; rerun: bash setup_android_sdk.sh --install'
    fi
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses
    echo 'Installing required Android SDK platform 35 and build-tools 35.0.1...'
    "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --install \
      'platforms;android-35' 'build-tools;35.0.1'
  fi
  write_local_properties
fi
[[ -f "$SDK/platforms/android-35/android.jar" ]] || fail "Android SDK platform 35 missing under '$SDK/platforms/android-35'. Run: bash setup_android_sdk.sh --install"
[[ -f "$SDK/build-tools/35.0.1/source.properties" ]] || fail "Android build-tools 35.0.1 missing under '$SDK'. Run: bash setup_android_sdk.sh --install"

if [[ "$ARCH" == 'aarch64' || "$ARCH" == 'arm64' ]]; then
  if ! AAPT2="$(find_native_aapt2)"; then
    if [[ -n "${BHARATAI_AAPT2:-}" ]]; then
      fail "Invalid explicit ARM64 AAPT2 override: '$BHARATAI_AAPT2'. Check the binary and host architecture. No other binary was tried."
    fi
    echo 'A compatible Linux ARM64 AAPT2 is missing.' >&2
    echo 'This project requires Android Build-Tools 35.0.1 with native ARM64 executables.' >&2
    echo 'The earlier suggestion to install Ubuntu aapt was incorrect: aapt is not aapt2.' >&2
    if [[ "$MODE" == '--install' && -t 0 && "${BHARATAI_NO_ARM64_PROMPT:-0}" != '1' ]]; then
      echo >&2
      echo 'A community-built ARM64 version is available from:' >&2
      echo 'https://github.com/Commit451/android-arm-build-tools/releases/tag/platform-tools-35.0.1' >&2
      echo 'This source is THIRD-PARTY (not Google). It will download 4 binaries as your' >&2
      echo 'normal user, verify published SHA256 sums and ELF architecture, and save' >&2
      echo 'backups of the original SDK tools. No sudo or APT is needed.' >&2
      read -r -p 'Do you approve installing these third-party ARM64 build tools? [y/N] ' reply || reply=''
      if [[ "$reply" =~ ^([yY]|[yY][eE][sS])$ ]]; then
        python3 "$ROOT/tools/install_arm64_buildtools.py" --sdk "$SDK" --install
      fi
    fi
    if ! AAPT2="$(find_native_aapt2)"; then
      cat >&2 <<'MSG'
ERROR: A runnable ARM64 AAPT2 is still required.
Option 1: Review the community release and install it explicitly:
  python3 tools/install_arm64_buildtools.py --sdk "$HOME/Android/Sdk" --install
  (Replace the SDK path if yours is elsewhere.)
Option 2: Supply your own trusted native tool:
  BHARATAI_AAPT2=/absolute/path/to/aapt2 bash build_android.sh
Option 3: Build the APK on an x86_64 Linux, Windows or macOS machine with Google's official SDK.
BharatAI does not require apt, and the invalid Microsoft APT source can be fixed separately.
MSG
      exit 3
    fi
  fi
  echo "ARM64 AAPT2: $AAPT2"
  printf 'BHARATAI_AAPT2=%q\n' "$AAPT2" > "$ROOT/.bharatai-arm64-aapt2.env"
else
  rm -f "$ROOT/.bharatai-arm64-aapt2.env"
fi
printf 'BHARATAI_SDK_DIR=%q\n' "$SDK" > "$ROOT/.bharatai-sdk.env"
echo "Android SDK found and configured: $SDK"
echo "Android SDK platform: 35 | Build-tools: 35.0.1 | Host: $ARCH"
echo 'Project local.properties configured. No model or dataset files modified.'
