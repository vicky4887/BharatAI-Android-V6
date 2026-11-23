# GitHub Actions SDK `tools` package failure — fixed

The prior workflow used `android-actions/setup-android@v3`. Its default package list
attempted `sdkmanager tools`, which is unavailable from Google's current SDK repository.

The workflow now uses `android-actions/setup-android@v4` with `packages: ''`.
The next step of the workflow still explicitly installs `platforms;android-35`,
`build-tools;35.0.1`, `ndk;27.2.12479018`, and `cmake;3.22.1`.

From EdgeXpert after extracting the patch into /home/vicky4887/myapp:

```bash
cd /home/vicky4887/myapp/BharatAI_Android_Offline_MVP
python3 -m unittest discover -s verification -p 'test_native_ci_bridge.py' -v
git diff -- .github/workflows/build-v6-native-apk.yml verification/test_native_ci_bridge.py
git add .github/workflows/build-v6-native-apk.yml verification/test_native_ci_bridge.py GITHUB_SDK_TOOLS_FIX.md
git commit -m 'Fix GitHub Android SDK deprecated tools package'
git push origin main
```

Then GitHub > Actions > Build BharatAI V6 Native Android APK > Run workflow.

This repairs the observed early SDK setup failure; it does not guarantee the
subsequent JNI/Gradle compilation will succeed.
