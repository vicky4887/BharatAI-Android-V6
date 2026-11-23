# BharatAI Android – AAPT2 subprocess-tree fix (EdgeXpert ARM64)

## Why this revision exists
The previous HANGFIX still produced `TimeoutExpired ... 20 seconds` on some Linux
ARM64 hosts for two intentional negative tests, even though `timeout` wrapped the
AAPT2 candidate. A shell-launched child can keep the calling process alive or
inherit captured descriptors. This revision uses a bounded Python probe with
`start_new_session=True`, `stdin/stdout/stderr=DEVNULL`, and `killpg(SIGKILL)` on timeout.

Invalid explicit AAPT2 paths now fail immediately. There is no fallback probe,
installation prompt, or SDK download in these negative tests. The model,
Gradle workflow, GitHub Actions workflow, and source code are preserved.

## Install
```bash
cd /home/vicky4887/myapp
unzip -o ~/Downloads/BharatAI_Android_V6_Native_GitHub_Build_Kit_AAPT2_PROCESS_FIX.zip -d /home/vicky4887/myapp
cd BharatAI_Android_Offline_MVP
```

## Verify bounded failure cases without any real SDK or network
```bash
python3 -m unittest discover -s verification -p 'test_sdk_arm64_fix.py' -v
python3 -m unittest discover -s verification -p 'test_*.py' -v
bash scripts/preflight_private_upload.sh
```

Expected: **46 tests, OK**. The intentional negative tests return errors from
their fake executables, but the test cases themselves must pass.

## Build native APK
A passing source check does not produce an APK. Follow the included
`.github/workflows/build-v6-native-apk.yml` on a private GitHub repository
with a Linux x86-64 runner. Check the status of that job separately.
