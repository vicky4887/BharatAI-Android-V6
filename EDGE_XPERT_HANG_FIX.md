# BharatAI EdgeXpert verification-hang fix

## Problem
The SDK validation test `test_arm64_without_native_aapt2_refuses_bad_binary` may hang on Linux ARM64 if a system AAPT2 binary blocks on `version`. The old setup script ran AAPT2 with no deadline and searched system binaries even when the caller supplied an explicit AAPT2 path.

## Changes
- `setup_android_sdk.sh` now probes AAPT2 using `timeout --kill-after=1s 3s`.
- Explicit `BHARATAI_AAPT2` paths are checked exclusively; if invalid, setup fails rather than silently falling back.
- `verification/test_sdk_arm64_fix.py` enforces a 20-second subprocess limit and has a regression test using an AAPT2 stub that sleeps for 30 seconds.
- All model files and native JNI source are unchanged.

## Install (complete replacement; no model or SDK downloads)

```bash
cd /home/vicky4887/myapp
unzip -o ~/Downloads/BharatAI_Android_V6_Native_GitHub_Build_Kit_HANGFIX.zip -d /home/vicky4887/myapp
cd BharatAI_Android_Offline_MVP
```

## Verify (safe and bounded)

```bash
python3 -m unittest discover -s verification -p 'test_sdk_arm64_fix.py' -v
python3 -m unittest discover -s verification -p 'test_*.py' -v
```

The full suite is **source verification only**, not Android APK compilation. You do not need a successful EdgeXpert native NDK build to proceed to the GitHub Actions native APK build.

## If you want to bypass slow SDK tests

Only when the repository preflight succeeded, you can run:

```bash
bash scripts/preflight_private_upload.sh
python3 -m unittest discover -s verification -p 'test_native_ci_bridge.py' -v
```

Then review `git status --short` and push only safe source files to a **private** GitHub repo.
