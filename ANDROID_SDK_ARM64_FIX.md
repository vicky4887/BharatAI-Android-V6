# BharatAI Android — DGX OS / EdgeXpert aarch64 build fix

This revision fixes the previous script's incorrect suggestion to `apt install aapt`.
`aapt` and `aapt2` are different tools. The bad `https://www.microsoft.com stable` APT
repository is a separate operating-system issue; **nothing in this build now runs sudo or apt**.

Your successful setup already has JDK 17, Gradle 8.7 and Android Platform 35.
This project now requests **Build-Tools 35.0.1** (official Google SDK package). We use 35.0.1 because
there is a community-produced **native Linux ARM64** Build-Tools 35.0.1 release.

## Install and build

Extract the ZIP over `/home/vicky4887/myapp/BharatAI_Android_Offline_MVP`.

```bash
cd /home/vicky4887/myapp
unzip -o ~/Downloads/BharatAI_Android_Offline_MVP_ARM64_AAPT2_REPAIR.zip -d /home/vicky4887/myapp
cd BharatAI_Android_Offline_MVP
bash build_android.sh
```

- The script reuses the existing Android SDK and installs official `build-tools;35.0.1` if absent.
- On ARM64, it explains the third-party source and **asks your approval before downloading binaries**.
- If approved, it fetches `aapt2`, `aidl`, `zipalign`, and `split-select` from the **pinned**
  [Commit451 35.0.1 release](https://github.com/Commit451/android-arm-build-tools/releases/tag/platform-tools-35.0.1).
  It checks the release's `SHA256SUMS`, checks the Linux ELF AArch64 machine type, backs up the original
  official SDK executables, and installs the community ones **without sudo**.
- The checksum manifest accompanies the third-party binaries. This helps detect accidental corruption,
  but **does not independently verify whether the publisher is trustworthy**. Review the linked project
  before choosing `y`. To avoid third-party native binaries altogether, build on an x86-64 computer
  with the official Android SDK instead.
- The app then runs tests and builds the APK at `app/build/outputs/apk/debug/app-debug.apk`.

You can separately install the ARM64 tools only after SDK 35.0.1 is installed:

```bash
python3 tools/install_arm64_buildtools.py --sdk "$HOME/Android/Sdk" --install
```

Or supply an already-installed trusted native AAPT2:

```bash
BHARATAI_AAPT2=/absolute/path/to/aapt2 bash build_android.sh
```

**No Android GGUF, training dataset, or model files are changed.**

## Diagnostics

```bash
bash check_build_jdk.sh
bash check_android_sdk.sh
file "$HOME/Android/Sdk/build-tools/35.0.1/aapt2"
"$HOME/Android/Sdk/build-tools/35.0.1/aapt2" version
bash build_android.sh --stacktrace
```

The app was not fully compiled in the packaging environment; your EdgeXpert still needs to execute
Gradle to verify the native toolchain and Kotlin sources. Further native-tool incompatibilities
might be reported then.

## Optional: repair the unrelated invalid APT source

If you also want `apt update` to work, identify the broken source **without changing it automatically**:

```bash
sudo grep -RnsE '(https?://)?(www\.)?microsoft\.com' \
  /etc/apt/sources.list /etc/apt/sources.list.d 2>/dev/null
```

Inspect the specific matching `.list` or `.sources` file and disable **only the invalid**
`https://www.microsoft.com stable` entry, retaining legitimate Microsoft, CUDA and NVIDIA
repositories. Use `sudoedit /path/to/matching/file` to comment the invalid `deb` entry or
remove only its stanza. There is no need to change APT configuration to build this project
with the native ARM64 release described above.
