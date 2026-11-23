"""Offline source-level integration assertions; doesn't pretend to compile Android NDK."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
PKG = ROOT / 'app/src/main/java/com/bharatai/education'


class MobileIntegrationTest(unittest.TestCase):
    def test_existing_solver_preserved(self):
        source = (PKG / 'core/VerifiedSolver.kt').read_text()
        for x in ('circuit', 'motion', 'triangle', 'enthalpy', 'punnett', 'mirror'):
            self.assertIn(f'"{x}"', source)

    def test_no_internet_permission(self):
        source = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('<uses-permission android:name="android.permission.INTERNET"', source)

    def test_native_opt_in(self):
        source = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertIn('bharataiNative', source)
        self.assertIn('if (bharataiNative)', source)
        self.assertIn('src/main/cpp/CMakeLists.txt', source)

    def test_jni_names_correspond(self):
        kotlin = (PKG / 'localai/NativeLlama.kt').read_text()
        cpp = (ROOT / 'app/src/main/cpp/bharatai_llm.cpp').read_text()
        for method in ('initialize', 'generate', 'release'):
            self.assertRegex(kotlin, r'external fun ' + method + r'\(')
            self.assertIn('Java_com_bharatai_education_localai_NativeLlama_' + method, cpp)

    def test_model_not_bundled(self):
        self.assertFalse(any(ROOT.glob('**/*.gguf')))
        self.assertIn('GGUF', (PKG / 'localai/ModelImport.kt').read_text())

    def test_import_uses_saf_and_private_storage(self):
        source = (PKG / 'localai/ModelImport.kt').read_text()
        self.assertIn('context.filesDir', source)
        self.assertIn('context.contentResolver.openInputStream', source)
        self.assertIn('FileOutputStream(tmp)', source)
        self.assertIn('magic.contentEquals', source)

    def test_explicit_confirmation_preserved(self):
        screen = (PKG / 'MainActivity.kt').read_text()
        self.assertIn('I checked every value', screen)
        self.assertIn('Button(enabled = confirmed', screen)
        self.assertIn('LocalProposal.interpretOrRule', screen)

    def test_native_cpu_and_small_context(self):
        src = (ROOT / 'app/src/main/cpp/bharatai_llm.cpp').read_text()
        self.assertIn('mp.n_gpu_layers = 0', src)
        self.assertIn('std::min(nctx, 2048)', src)
        self.assertIn('llama_memory_clear', src)
        self.assertIn('llama_model_load_from_file', src)

    def test_native_host_gate(self):
        src = (ROOT / 'scripts/build_with_native.sh').read_text()
        self.assertIn('uname -m', src)
        self.assertIn('x86_64', src)

    def test_ui_offline_controls(self):
        src = (PKG / 'MainActivity.kt').read_text()
        for label in ('Import GGUF', 'Load V6', 'Interpret with V6', 'Unload'):
            self.assertIn(label, src)


if __name__ == '__main__':
    unittest.main()
