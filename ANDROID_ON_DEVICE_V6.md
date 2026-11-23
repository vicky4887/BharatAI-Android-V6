# BharatAI Android — local Qwen3 V6, integration stage

This is the **source implementation** for on-device text-model inference, not a claimed verified Android APK.
It is based on the latest prior BharatAI Kotlin Compose + offline OCR/solver project and retains those components.

## What works in the source

- UI: **Import GGUF**, **Load V6**, **Interpret with V6**, **Unload**.
- Android Storage Access Framework file picker, GGUF magic signature check, bounded copy into app-private storage for memory mapping.
- Synchronous llama.cpp JNI entry points executed from Kotlin IO coroutines (not on the UI thread).
- CPU-only quantized GGUF load, 2048 token context, 3 threads initially, bounded output 256 tokens.
- A *non-thinking* Qwen3 prompt asks V6 for a structured proposal only; never uses its arithmetic to answer.
- A JSON proposal validator with allowlisted kinds/targets/fields and mandatory deterministic `VerifiedSolver.solve()` checks.
- Rule parser is shown with an **explicit fallback notice**, not represented as a model success.
- User confirms all values before the deterministic solver is allowed to run.
- No `android.permission.INTERNET` and no network service required on the device.
- Existing OCR/camera/gallery/solver flows remain usable even without the native JNI component.

## Project directory on EdgeXpert

`/home/vicky4887/myapp/BharatAI_Android_Offline_MVP`

Your GGUF remains in:

`/home/vicky4887/myapp/output/stage6_diagrams/gguf/BharatAI-V6-DiagramText-Q4_K_M.gguf`

The GGUF **is not contained in the ZIP or APK**. Transfer it separately to the phone; the file picker imports it to app-private storage. Free storage must accommodate the source GGUF and a second imported copy.

## Build modes

### A) EdgeXpert ARM64: build original OCR + solver APK (native V6 not included)

```bash
cd /home/vicky4887/myapp/BharatAI_Android_Offline_MVP
bash build_android.sh
```

The app visibly says when JNI is not built. It does **not** pretend that inference is running; manual and OCR-based solving remain functional.

### B) x86_64 Linux host: build APK with local llama.cpp JNI

Official Android NDK host tools normally target **x86_64 Linux**, not the EdgeXpert's aarch64 OS. Building a native JNI module on the EdgeXpert is not guaranteed merely by fixing ARM64 AAPT2.

Copy/extract this source project on an **x86_64 Linux** machine with JDK17, a working Android SDK, internet for build dependencies, Git and ~10 GB free disk. Then:

```bash
cd BharatAI_Android_Offline_MVP
bash scripts/build_with_native.sh
```

This build helper installs SDK NDK 27.2 and CMake 3.22.1, retrieves upstream llama.cpp source, and runs `:app:testDebugUnitTest :app:assembleDebug` with `-PbharataiNative=true`.

**Source version reproducibility:** `fetch_llama_source.sh` downloads upstream `main` at build time. Run `git -C app/src/main/cpp/llama.cpp rev-parse HEAD`, record the exact revision, inspect its upstream license, and pin the same revision for a production build. The JNI bridge follows llama.cpp's current `examples/simple` APIs, but **has not been compiled and run against a specific upstream revision in this environment**. Library API or CMake compatibility adjustments may be needed.

The default `bash build_android.sh` builds **without** the JNI library. The native-capable build is explicitly opt-in. The APK filename is the same path in both modes; ensure the build mode before installing.

## Transfer model onto your Android phone

Use a computer with Android Debug Bridge and USB debugging enabled:

```bash
adb push /home/vicky4887/myapp/output/stage6_diagrams/gguf/BharatAI-V6-DiagramText-Q4_K_M.gguf \
  /sdcard/Download/BharatAI-V6-DiagramText-Q4_K_M.gguf
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `adb push` command must be run on the computer where that `.gguf` path exists (or update the source path). Alternatively transfer the GGUF through USB file transfer. Inside the app, tap **Import GGUF** and select the copied file in Downloads. Then tap **Load V6**. File copying may take time and requires adequate free phone storage.

## First test (airplane mode)

1. Select the V6 GGUF from Downloads and import it.
2. Load V6 and confirm the status says "Model loaded on device (CPU)".
3. Enter: `A 20 V battery is connected to 17 ohm and 3 ohm resistors in series. Find the current.`
4. Tap **Interpret with V6**. Read whether V6 supplied the values or the **rules fallback** did.
5. Verify `kind=circuit`, `target=current`, `connection=series`, `resistors_ohm=17,3`, `voltage_v=20`.
6. Check confirmation and tap **Calculate verified answer**. Expected **1 A**.
7. Try other supported examples, use **Unload** before testing memory pressure.

This application **cannot interpret diagram pixels** yet. OCR extracts printed text only; the Qwen3 V6 model is a text model. Students must still confirm diagram values manually.

## If something fails

- **No native runtime**: You built the default, non-native APK. Build the native variant on x86_64.
- **GGUF load fails / memory pressure**: verify file integrity, 6 GB phone actual free RAM, unload other apps; reduce context to 1024 in `MainActivity.kt` for a diagnostic rebuild.
- **V6 outputs invalid JSON**: the UI labels the rules fallback, if available. It never trusts a model-generated solution.
- **Android Gradle fails on EdgeXpert**: see `ANDROID_SDK_ARM64_FIX.md`. AAPT2 and NDK host-tool support are separate challenges.

## Validation scope

Existing 25 Kotlin solver checks and the new Python source-level checks pass locally. The project contains Android unit tests for the proposal adapter. **No native-enabled APK has been compiled or executed in this environment; inference speed, RAM, and device compatibility are not measured.**

## Next milestones

1. Confirm actual native-enabled APK build and device load with your GGUF.
2. Measure cold-start, RSS/peak memory, and latency on the target 6 GB Android device.
3. Add offline vision for scientific diagrams only after stable text-model inference; never claim OCR equals diagram understanding.
