import unittest, pathlib, re, tempfile, subprocess, os, shutil
R=pathlib.Path(__file__).resolve().parents[1]

class NativeCIBridgeTests(unittest.TestCase):
 def test_workflow_registered(self):
  w=(R/'.github/workflows/build-v6-native-apk.yml').read_text()
  for t in ('workflow_dispatch:', 'runs-on: ubuntu-24.04', 'java-version:', "'17'", 'setup-android@v3', 'ndk;27.2.12479018', 'cmake;3.22.1', 'build_native_ci.sh'):
   self.assertIn(t,w)
 def test_native_required_and_artifact_verified(self):
  w=(R/'.github/workflows/build-v6-native-apk.yml').read_text()
  b=(R/'scripts/build_native_ci.sh').read_text()
  self.assertIn('-PbharataiNative=true', b)
  for t in ('lib/arm64-v8a/libbharatai_llm.so', 'upload-artifact@v4','if-no-files-found: error'):
   self.assertIn(t,w)
  self.assertIn('lib/arm64-v8a/libbharatai_llm.so', b)
 def test_native_only_production_source_and_model_not_bundled(self):
  self.assertFalse(any(R.rglob('*.gguf')))
  self.assertIn('app/src/main/cpp/llama.cpp/',(R/'.gitignore').read_text())
  self.assertNotIn('android.permission.INTERNET',(R/'app/src/main/AndroidManifest.xml').read_text())
 def test_version_pin(self):
  s=(R/'scripts/fetch_llama_source.sh').read_text()
  self.assertIn("TAG='v0.6.0'",s)
  self.assertIn('git clone --depth 1 --branch "$TAG"',s)
  self.assertNotIn('git clone --depth 1 https://github.com/ggml-org/llama.cpp.git', s)
 def test_actual_jni(self):
  code=(R/'app/src/main/cpp/bharatai_llm.cpp').read_text()
  for t in ('llama_model_load_from_file','llama_process','llama_batch_ext_add_token','llama_memory_clear','mp.n_gpu_layers = 0'):
   self.assertIn(t,code)
  self.assertIn('arm64-v8a',(R/'app/build.gradle.kts').read_text())
 def test_host_gate_no_ndk(self):
  env=os.environ.copy(); env.pop('ANDROID_HOME',None)
  p=subprocess.run(['bash','scripts/build_native_ci.sh'], cwd=R, env=env, text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=10)
  self.assertNotEqual(p.returncode,0)
  self.assertTrue('ANDROID_HOME' in p.stderr or 'x86_64' in p.stderr)
 def test_github_actions_yaml_valid(self):
  try: import yaml
  except ImportError: self.skipTest('PyYAML not installed')
  d=yaml.safe_load((R/'.github/workflows/build-v6-native-apk.yml').read_text())
  self.assertEqual(d['jobs']['native-apk']['runs-on'],'ubuntu-24.04')
 def test_preflight(self):
  out=subprocess.run(['bash','scripts/preflight_private_upload.sh'],cwd=R,text=True,capture_output=True,timeout=15)
  self.assertEqual(out.returncode,0,out.stderr)

if __name__=='__main__': unittest.main(verbosity=2)
