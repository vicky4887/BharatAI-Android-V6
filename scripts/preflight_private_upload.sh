#!/usr/bin/env bash
# Check for accidental model/secrets inclusion before manually pushing private source.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PYINNER'
from pathlib import Path
p=Path('.').resolve()
for name in ('.github/workflows/build-v6-native-apk.yml','app/build.gradle.kts','scripts/build_native_ci.sh'):
    assert (p/name).is_file(), name
assert not (p/'app/src/main/cpp/llama.cpp').exists() or (p/'app/src/main/cpp/llama.cpp/.git').is_dir(), 'Unexpected vendored llama source'
exclude={'.git','.gradle','.gradle-dist','build','.idea','dist'}
for f in p.rglob('*'):
    if f.is_dir(): continue
    if any(part in exclude for part in f.relative_to(p).parts): continue
    if f.suffix.lower() in {'.gguf','.safetensors','.ckpt','.jks','.keystore','.pem','.pth','.pt'}:
        raise SystemExit('STOP: Sensitive/model file found: '+str(f))
print('Preflight: no model weight or signing-key files found in project folder.')
print('Important: review `git status --short` before pushing to a private repository.')
PYINNER
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    bad="$(git ls-files --cached | grep -Ei '\.(gguf|safetensors|ckpt|jks|keystore|pem|pth|pt)$' || true)"
    if [[ -n "$bad" ]]; then
       echo "STOP: sensitive file already staged/tracked: $bad" >&2; exit 3
    fi
fi
