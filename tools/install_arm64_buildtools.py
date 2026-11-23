#!/usr/bin/env python3
"""Opt-in installer for a pinned community ARM64 Android Build-Tools release.

No sudo, no APT, no shell execution of downloaded scripts. Downloads SHA256SUMS
and four ELF binaries, validates SHA256 and ARM64 machine type before execution,
backs up original Google binaries, installs into an existing SDK build-tools folder.

The release is third-party, NOT official Google binaries. Review and choose whether
to trust the maintainer before invoking with --install.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request

VERSION = '35.0.1'
TAG = 'platform-tools-35.0.1'
URL_BASE = f'https://github.com/Commit451/android-arm-build-tools/releases/download/{TAG}'
TOOLS = ('aapt2', 'aidl', 'zipalign', 'split-select')
EM_AARCH64 = 183


def parse_checksums(data: str) -> dict[str, str]:
    """Accept plain sha256sum lines for the four expected tool names."""
    out: dict[str, str] = {}
    pat = re.compile(r'^([0-9a-fA-F]{64})\s+\*?(?:\./)?(?:[A-Za-z0-9_.-]+/)*([A-Za-z0-9_-]+)\s*$')
    for line in data.splitlines():
        m = pat.fullmatch(line.strip())
        if not m:
            continue
        digest, name = m.groups()
        if name in TOOLS:
            if name in out:
                raise ValueError(f'Duplicate SHA256 checksum entry for {name}')
            out[name] = digest.lower()
    missing = set(TOOLS) - set(out)
    if missing:
        raise ValueError(f'Checksum manifest missing {sorted(missing)}')
    return out


def is_arm64_elf(path: Path) -> bool:
    """Inspect ELF e_machine without executing the file."""
    try:
        with path.open('rb') as fh:
            hdr = fh.read(20)
        return (
            len(hdr) == 20 and hdr[:4] == b'\x7fELF' and
            hdr[4] == 2 and hdr[5] in (1, 2) and
            int.from_bytes(hdr[18:20], 'little' if hdr[5] == 1 else 'big') == EM_AARCH64
        )
    except (OSError, ValueError):
        return False


def download(url: str, dest: Path) -> None:
    # urllib handles HTTPS redirects from GitHub release pages to asset storage.
    request = urllib.request.Request(url, headers={
        'User-Agent': 'BharatAI-ARM64-BuildTools-Installer/1.0',
        'Accept': 'application/octet-stream',
    })
    with urllib.request.urlopen(request, timeout=90) as response, dest.open('wb') as fh:
        shutil.copyfileobj(response, fh)


def install(sdk: Path) -> None:
    sdk = sdk.expanduser().resolve()
    target = sdk / 'build-tools' / VERSION
    if not (target / 'source.properties').is_file():
        raise RuntimeError(
            f'Official Android build-tools {VERSION} not found at {target}.\n'
            f'Install first using sdkmanager "build-tools;{VERSION}".'
        )
    if platform.system() != 'Linux' or platform.machine().lower() not in ('aarch64', 'arm64'):
        raise RuntimeError('Native ARM64 tools installer runs only on Linux aarch64.')

    print(f'Source: {URL_BASE}/')
    print('Source is THIRD-PARTY, not official Google Android Build-Tools.')
    print('Checksum comes from the same release; it checks integrity but NOT trustworthiness.')
    print(f'SDK destination: {target}')

    # Stage all data under SDK to make final renames local and atomic.
    with tempfile.TemporaryDirectory(prefix='.bharatai-arm64-', dir=target) as tmp:
        stage = Path(tmp)
        manifest = stage / 'SHA256SUMS'
        download(URL_BASE + '/SHA256SUMS', manifest)
        checksums = parse_checksums(manifest.read_text(encoding='utf-8'))
        for tool in TOOLS:
            artifact = stage / tool
            print(f'Downloading {tool}...')
            download(URL_BASE + '/' + tool, artifact)
            actual = hashlib.sha256(artifact.read_bytes()).hexdigest()
            if actual != checksums[tool]:
                raise RuntimeError(f'Checksum mismatch: {tool}. No SDK tool was replaced.')
            if not is_arm64_elf(artifact):
                raise RuntimeError(f'{tool} is not a Linux ARM64 ELF. No SDK tool was replaced.')
            artifact.chmod(0o755)

        # Keep originals intact so a user can restore the official SDK binaries.
        backup = target / '.bharatai-original-google-buildtools'
        backup.mkdir(exist_ok=True)
        for tool in TOOLS:
            orig = target / tool
            dst = backup / tool
            if orig.is_file() and not dst.exists():
                shutil.copy2(orig, dst)

        # Restore originals if a subsequent copy/replace fails (best-effort rollback).
        replaced: list[str] = []
        try:
            for tool in TOOLS:
                os.replace(stage / tool, target / tool)
                replaced.append(tool)
            proc = subprocess.run([str(target / 'aapt2'), 'version'],
                                  capture_output=True, text=True, timeout=15)
            if proc.returncode != 0:
                raise RuntimeError(f'Installed ARM64 aapt2 failed to run: {proc.stderr.strip()}')
        except Exception:
            for tool in replaced:
                original = backup / tool
                if original.is_file():
                    shutil.copy2(original, target / tool)
            raise

    print('Native ARM64 build tools installed successfully.')
    print(f'AAPT2: {target / "aapt2"}')
    print('Original Google SDK binaries backed up in:')
    print(target / '.bharatai-original-google-buildtools')
    print('Run: bash build_android.sh')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, help='Absolute Android SDK path')
    parser.add_argument('--install', action='store_true', help='Explicitly approve downloading third-party tools')
    args = parser.parse_args()
    if not args.install:
        parser.error('No changes made. Explicit --install flag is required.')
    try:
        install(Path(args.sdk))
    except Exception as exc:
        print(f'ERROR: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
