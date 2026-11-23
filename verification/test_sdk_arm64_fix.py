"""No downloads/no Gradle needed: exercises SDK discovery and ARM64 AAPT2 handling."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SDKFixTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        p = Path(self.tmp.name)
        self.project = p / 'BharatAI_Android_Offline_MVP'
        (self.project / 'scripts').mkdir(parents=True)
        for name in ('setup_android_sdk.sh', 'build_android.sh', 'check_android_sdk.sh'):
            shutil.copy2(ROOT / name, self.project / name)
        shutil.copy2(ROOT / 'scripts/select_build_jdk.sh', self.project / 'scripts/select_build_jdk.sh')
        self.sdk = p / 'SDK'
        (self.sdk / 'platforms/android-35').mkdir(parents=True)
        (self.sdk / 'build-tools/35.0.1').mkdir(parents=True)
        (self.sdk / 'platforms/android-35/android.jar').write_bytes(b'mock')
        (self.sdk / 'build-tools/35.0.1/source.properties').write_text('Pkg.Revision=35.0.1\n')

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, *args, **additional_env):
        env = os.environ.copy()
        env.update({'BHARATAI_SDK': str(self.sdk), 'BHARATAI_TEST_ARCH': 'x86_64'})
        env.update(additional_env)
        return subprocess.run(['bash', str(self.project / 'setup_android_sdk.sh'), *args],
                              capture_output=True, text=True, env=env, timeout=20)

    def test_sdk_detected_and_properties_written(self):
        p = self.run_script('--install')
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn('sdk.dir=', (self.project / 'local.properties').read_text())
        self.assertIn(str(self.sdk), (self.project / 'local.properties').read_text())
        self.assertTrue((self.project / '.bharatai-sdk.env').exists())

    def test_sdk_check_read_only(self):
        p = self.run_script('--check')
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertFalse((self.project / 'local.properties').exists())

    def test_missing_sdk_returns_actionable_error(self):
        (self.sdk / 'platforms/android-35/android.jar').unlink()
        p = self.run_script('--check')
        self.assertNotEqual(p.returncode, 0)
        self.assertTrue('Android SDK' in p.stderr)
        self.assertIn('setup_android_sdk.sh', p.stderr)

    def test_arm64_with_native_aapt2_accepted(self):
        native = Path(self.tmp.name) / 'native-aapt2'
        native.write_text('#!/bin/sh\nif [ "$1" = version ]; then echo "Android Asset Packaging Tool (aapt) 2"; else exit 1; fi\n')
        native.chmod(0o755)
        p = self.run_script('--install', BHARATAI_TEST_ARCH='aarch64', BHARATAI_AAPT2=str(native))
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertIn(str(native), (self.project / '.bharatai-arm64-aapt2.env').read_text())

    def test_arm64_without_native_aapt2_refuses_bad_binary(self):
        old = Path(self.tmp.name) / 'bad-aapt2'
        old.write_bytes(b'not a real executable')
        old.chmod(0o755)
        p = self.run_script('--install', BHARATAI_TEST_ARCH='aarch64', BHARATAI_AAPT2=str(old))
        self.assertNotEqual(p.returncode, 0)
        self.assertIn('ARM64 AAPT2', p.stderr)

    def test_explicit_hanging_aapt2_is_bounded_and_rejected(self):
        # Regression for an EdgeXpert test hanging forever while checking AAPT2.
        slow = Path(self.tmp.name) / 'hung-aapt2'
        slow.write_text('#!/bin/sh\nsleep 30\n')
        slow.chmod(0o755)
        import time
        start = time.monotonic()
        p = self.run_script('--install', BHARATAI_TEST_ARCH='aarch64', BHARATAI_AAPT2=str(slow))
        self.assertNotEqual(p.returncode, 0)
        self.assertLess(time.monotonic() - start, 12)
        self.assertIn('ARM64 AAPT2', p.stderr)

    def test_hanging_aapt2_with_child_is_killed_without_pipe_leak(self):
        # A shell that launches a child can keep inherited descriptors open.
        # The probe must terminate its full process group and never capture those pipes.
        slow = Path(self.tmp.name) / 'hung-child-aapt2'
        slow.write_text('#!/bin/sh\nsleep 35 &\nwait\n')
        slow.chmod(0o755)
        import time
        start = time.monotonic()
        p = self.run_script('--install', BHARATAI_TEST_ARCH='aarch64', BHARATAI_AAPT2=str(slow))
        self.assertNotEqual(p.returncode, 0)
        self.assertLess(time.monotonic() - start, 10)
        self.assertIn('ARM64 AAPT2', p.stderr)

    def test_missing_platform_fails_without_fake_success(self):
        (self.sdk / 'platforms/android-35/android.jar').unlink()
        p = self.run_script('--check', BHARATAI_TEST_ARCH='aarch64')
        self.assertNotEqual(p.returncode, 0)
        self.assertFalse((self.project / '.bharatai-sdk.env').exists())

    def test_auto_sdk_check_in_build_script(self):
        script = (ROOT / 'build_android.sh').read_text()
        self.assertIn('setup_android_sdk.sh', script)
        self.assertIn('ANDROID_HOME=', script)
        self.assertIn('android.aapt2FromMavenOverride', script)

    def test_bash_syntax(self):
        for filename in ('setup_android_sdk.sh', 'build_android.sh', 'check_android_sdk.sh'):
            subprocess.run(['bash', '-n', str(ROOT / filename)], check=True)

if __name__ == '__main__':
    unittest.main()
