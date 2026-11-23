# Build & verification notes

- AGP: 8.6.1; Gradle: 8.7; Kotlin: 2.0.21; Java: 17; compileSdk 35; minSdk 26.
- Compose compiler Gradle plugin is explicit for Kotlin 2.0.
- Bundled OCR is `com.google.mlkit:text-recognition:16.0.1` (Latin script), not the dynamic Play Services variant.
- FileProvider uses an app-private cache path for camera captures; no broad storage permission is needed.
- The Android manifest deliberately declares **no internet permission**.
- Gradle official wrapper jar cannot be packaged automatically without fetching Gradle. `bash bootstrap_gradle.sh` downloads a pinned, SHA256-verified distribution and executes the official `wrapper` task.
- Error during Gradle sync `SDK location not found`: set `ANDROID_HOME` and ensure SDK Platform 35 is installed; Android Studio can create `local.properties`.
- Error `Java 17`: in Android Studio set Gradle JDK 17, or export `JAVA_HOME`.
- Error `OCR returns incorrect symbols`: manually correct OCR text; diagram parsing not supported.
- Error `Question not recognized`: select the family and fill the structured fields manually.
- Error `Invalid problem`: review the conditions in the UI and solution assumptions.
- The package deliberately contains **no compiled APK** and **no model GGUF**.

## Gradle Java 8 variant mismatch fix (2026-11-23)

`Gradle 8.7` + AGP `8.6.1` must run under compatible full JDK; use JDK 17 (or 21), not Java 8 or 22. The `Compiling and testing with Java 22` text is from Gradle release notes, **not proof of your runtime JDK**. The new one-command build is:

```bash
cd /home/vicky4887/myapp/BharatAI_Android_Offline_MVP
bash check_build_jdk.sh
bash build_android.sh
```

If JDK 17 is missing: `sudo apt update && sudo apt install -y openjdk-17-jdk`. Override auto-detection with `BHARATAI_JDK=/path/to/jdk17 bash build_android.sh`. Set `ANDROID_HOME` to your installed Android SDK if necessary. The bootstrap generates the official Gradle wrapper in a temporary minimal project to avoid prematurely resolving the Android plugin. `-Dorg.gradle.java.home` is passed to every Gradle invocation to override stale Gradle JVM settings.

## EdgeXpert ARM64: SDK discovery/installation (SDKFIX)

After a successful JDK 17 selection, `SDK location not found` is fixed by the new setup script:

```bash
bash build_android.sh
```

This runs `setup_android_sdk.sh --install`, checks/installs the official Android SDK API35 and Build-Tools35, requests interactive SDK license acceptance where required, and writes `local.properties`. On Linux ARM64 it verifies native AAPT2 and offers an Ubuntu package installation prompt (requires explicit consent and sudo). For details see `ANDROID_SDK_ARM64_FIX.md`.
