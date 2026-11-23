"""Network-free tests for release verification and failure-safe native SDK installation."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('arm64_tools', ROOT / 'tools/install_arm64_buildtools.py')
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def elf(arch=183):
    data = bytearray(64)
    data[:4] = b'\x7fELF'
    data[4] = 2
    data[5] = 1
    data[18:20] = arch.to_bytes(2, 'little')
    return bytes(data)


class ARM64InstallerTests(unittest.TestCase):
    def test_pinned_release(self):
        self.assertEqual(module.VERSION, '35.0.1')
        self.assertIn('/platform-tools-35.0.1', module.URL_BASE)

    def test_checksum_parse(self):
        text = '\n'.join(f'{i+1:064x}  {name}' for i, name in enumerate(module.TOOLS))
        got = module.parse_checksums(text)
        self.assertEqual(set(got), set(module.TOOLS))
        self.assertEqual(got['aapt2'], f'{1:064x}')
        self.assertEqual(module.parse_checksums(text.replace('  aidl', ' *out/linux-arm64/aidl')), got)

    def test_missing_checksum_rejected(self):
        with self.assertRaises(ValueError):
            module.parse_checksums('f' * 64 + '  aapt2')

    def test_duplicate_checksum_rejected(self):
        data = '\n'.join(f'{i+1:064x}  {name}' for i, name in enumerate(module.TOOLS))
        with self.assertRaises(ValueError):
            module.parse_checksums(data + '\n' + ('f' * 64) + '  aapt2')

    def test_elf_machine_detection(self):
        with tempfile.TemporaryDirectory() as td:
            f = Path(td) / 'binary'
            f.write_bytes(elf())
            self.assertTrue(module.is_arm64_elf(f))
            f.write_bytes(elf(62))
            self.assertFalse(module.is_arm64_elf(f))
            f.write_bytes(b'#!/bin/sh\n')
            self.assertFalse(module.is_arm64_elf(f))

    def test_explicit_consent_required(self):
        proc = subprocess.run([
            sys.executable, str(ROOT / 'tools/install_arm64_buildtools.py'),
            '--sdk', '/tmp/not-real'
        ], capture_output=True, text=True)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn('--install', proc.stderr)

    def test_good_download_installs_with_backups(self):
        with tempfile.TemporaryDirectory() as td:
            target = Path(td) / 'SDK/build-tools/35.0.1'
            target.mkdir(parents=True)
            (target / 'source.properties').write_text('Pkg.Revision=35.0.1\n')
            for tool in module.TOOLS:
                (target / tool).write_bytes(b'old Google binary')
            blobs = {name: elf() + name.encode() for name in module.TOOLS}
            checksums = '\n'.join(
                hashlib.sha256(data).hexdigest() + '  ' + name
                for name, data in blobs.items()
            ).encode()

            def fake_download(url, dest):
                dest.write_bytes(checksums if url.endswith('SHA256SUMS')
                                 else blobs[url.rsplit('/', 1)[-1]])
            with patch.object(module, 'download', side_effect=fake_download), \
                 patch.object(module.platform, 'machine', return_value='aarch64'), \
                 patch.object(module.platform, 'system', return_value='Linux'), \
                 patch.object(module.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, 'aapt2 v2', '')):
                module.install(Path(td) / 'SDK')
            self.assertEqual((target / 'aapt2').read_bytes(), blobs['aapt2'])
            self.assertEqual((target / '.bharatai-original-google-buildtools/aapt2').read_bytes(), b'old Google binary')

    def test_bad_checksum_does_not_overwrite_sdk(self):
        with tempfile.TemporaryDirectory() as td:
            target = Path(td) / 'SDK/build-tools/35.0.1'
            target.mkdir(parents=True)
            (target / 'source.properties').write_text('Pkg.Revision=35.0.1\n')
            (target / 'aapt2').write_bytes(b'untouched')
            sums = '\n'.join(('f' * 64) + '  ' + name for name in module.TOOLS)

            def fake_download(url, dest):
                dest.write_bytes(sums.encode() if url.endswith('SHA256SUMS') else elf())
            with patch.object(module, 'download', side_effect=fake_download), \
                 patch.object(module.platform, 'machine', return_value='aarch64'), \
                 patch.object(module.platform, 'system', return_value='Linux'):
                with self.assertRaisesRegex(RuntimeError, 'Checksum mismatch'):
                    module.install(Path(td) / 'SDK')
            self.assertEqual((target / 'aapt2').read_bytes(), b'untouched')

    def test_never_sudo_or_apt(self):
        text = (ROOT / 'setup_android_sdk.sh').read_text()
        self.assertNotIn('sudo apt-get', text)
        self.assertNotIn('apt-get install', text)
        self.assertIn('install_arm64_buildtools.py', text)

    def test_android_uses_35_0_1_and_overrides_gradle(self):
        self.assertIn('buildToolsVersion = "35.0.1"', (ROOT / 'app/build.gradle.kts').read_text())
        self.assertIn('android.aapt2FromMavenOverride', (ROOT / 'build_android.sh').read_text())


if __name__ == '__main__':
    unittest.main()
