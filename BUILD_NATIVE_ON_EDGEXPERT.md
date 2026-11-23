# BharatAI V6 — Native APK build from EdgeXpert using GitHub Actions

**Goal:** Obtain an Android `arm64-v8a` APK with native `llama.cpp` JNI despite the MSI EdgeXpert running ARM64 Linux. The build uses a **hosted x86_64 runner**, while the installed Android app runs entirely **offline**.

The project folder is `BharatAI_Android_Offline_MVP/`. It contains complete app source (no `.gguf`). **Do not upload the parent `/home/vicky4887/myapp` directory.**

## One-time setup on EdgeXpert

Unzip the package *into* `/home/vicky4887/myapp`, then:

```bash
cd /home/vicky4887/myapp/BharatAI_Android_Offline_MVP
python3 verification/check_project.py
python3 -m unittest discover -s verification -p 'test_*.py'
bash run_core_tests.sh
```

## Build the JNI APK with GitHub Actions

1. Sign in to GitHub and create an **EMPTY PRIVATE** repository, for example `BharatAI-Android-V6` (do NOT initialize it with a README). A private repository helps keep your app source private, but GitHub processes that source to build it. Review their policies before uploading.
2. Git from your EdgeXpert must be authenticated to your GitHub repository (SSH or credential manager). In the app **project folder** only, run:

```bash
cd /home/vicky4887/myapp/BharatAI_Android_Offline_MVP
bash scripts/preflight_private_upload.sh
git init -b main
git add .
git status --short
# Check that no model weights or private files are staged.
git commit -m 'BharatAI offline Android V6 native build'
git remote add origin git@github.com:YOUR_GITHUB_USERNAME/BharatAI-Android-V6.git
git push -u origin main
```

Replace `YOUR_GITHUB_USERNAME`. If `git init`/`remote add` complains about an existing repository/remote, inspect `git status` and `git remote -v` first—do not overwrite existing history. To use HTTPS instead of SSH, use an authenticated HTTPS remote.

3. On GitHub open your private repository → **Actions** → **Build BharatAI V6 Native Android APK** → **Run workflow**. `push` to `main` also triggers the workflow.
4. After a successful run, open the workflow run → **Artifacts** → download `BharatAI-V6-Native-Android-debug`.
5. Extract the downloaded artifact ZIP. It contains:

```
BharatAI-V6-Native-Offline-arm64-debug.apk
SHA256SUMS.txt
BUILD_INFO.txt
```

Check the SHA-256 checksum before installing. Debug APK is intended for internal testing, not Play Store submission.

## Transfer V6 GGUF privately (not via GitHub)

Copy your existing model from the EdgeXpert to the phone with a cable or your usual local file-transfer method:

```
/home/vicky4887/myapp/output/stage6_diagrams/gguf/BharatAI-V6-DiagramText-Q4_K_M.gguf
```

Install the APK (via ADB on a machine with ADB and phone attached, or tap APK on phone). In the app:

1. Import GGUF from Android Downloads.
2. Load V6; verify load status.
3. Type `A 20 V battery has 17 ohm and 3 ohm resistors in series. Calculate current.`
4. Interpret with V6, check **all extracted inputs** before confirmation.
5. Confirm values, solve: expected **1 A**.
6. Repeat with **airplane mode enabled**.

## Key constraints

- Hosted build requires internet **during compilation**. The finished app has no INTERNET permission and can work offline.
- The application implements **text-only** GGUF inference. It cannot interpret diagram pixels. OCR captures printed text, not diagrams.
- A native APK that compiles successfully is **not proof** that a 6 GB phone can load this GGUF or generate tokens at acceptable speed. Measure memory and latency on a real phone.
- A failed GitHub Actions job means the next blocker is in the compiler/dependency logs, not the EdgeXpert's ARM64 NDK host issue. Record the first compiler error, not the last Gradle summary.
- The workflow fetches llama.cpp `v0.6.0` instead of the unstable `master` branch; there may still be API differences or compilation failures requiring code changes. Never label the APK complete until GitHub reports a successful build and the artifact includes `libbharatai_llm.so`.
- Build artifacts in private repositories may consume GitHub Actions minutes or storage and have retention limits.

## What the workflow actually does

- Uses Ubuntu 24.04 **x86_64** plus JDK 17.
- Installs official Android API 35, Build-Tools 35.0.1, NDK 27.2 and CMake 3.22.1.
- Fetches pinned llama.cpp v0.6.0 and builds `arm64-v8a` JNI via CMake.
- Runs unit tests, builds debug APK, verifies `lib/arm64-v8a/libbharatai_llm.so` is in APK.
- Publishes APK, SHA256 checksum and build metadata as GitHub Actions artifacts.

The workflow **does not upload or download your model**. Keep your training data on EdgeXpert.
