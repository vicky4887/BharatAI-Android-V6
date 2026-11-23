"""Offline checks for JDK/Gradle build compatibility and project content."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SELECT = ROOT / 'scripts/select_build_jdk.sh'

class JDKSelectorTests(unittest.TestCase):
    def test_scripts_bash_syntax(self):
        for script in ('scripts/select_build_jdk.sh', 'bootstrap_gradle.sh', 'build_android.sh', 'check_build_jdk.sh'):
            subprocess.run(['bash', '-n', str(ROOT / script)], check=True)

    def _fake_jdk(self, version):
        tmp=tempfile.TemporaryDirectory()
        home=Path(tmp.name)
        (home/'bin').mkdir()
        java=home/'bin/java'
        java.write_text(f'#!/bin/sh\necho \'openjdk version "{version}.0.2"\' >&2\n')
        javac=home/'bin/javac'
        javac.write_text(f'#!/bin/sh\necho \'javac {version}.0.2\'\n')
        java.chmod(0o755)
        javac.chmod(0o755)
        return tmp,home

    def test_java17_override_accepted(self):
        tmp,home=self._fake_jdk(17)
        with tmp:
            env={**os.environ,'BHARATAI_JDK':str(home)}
            proc=subprocess.run(['bash','-c',f'source "{SELECT}"; bharatai_select_jdk; echo "JAVA_HOME=$JAVA_HOME"'],
                                capture_output=True,text=True,env=env)
            self.assertEqual(proc.returncode,0,proc.stderr)
            self.assertIn(f'JAVA_HOME={home}',proc.stdout)

    def test_java21_override_accepted(self):
        tmp,home=self._fake_jdk(21)
        with tmp:
            proc=subprocess.run(['bash','-c',f'source "{SELECT}"; bharatai_select_jdk'],
                                capture_output=True,text=True,env={**os.environ,'BHARATAI_JDK':str(home)})
            self.assertEqual(proc.returncode,0,proc.stderr)

    def test_java8_rejected(self):
        tmp,home=self._fake_jdk(8)
        with tmp:
            proc=subprocess.run(['bash','-c',f'source "{SELECT}"; bharatai_select_jdk'],
                                capture_output=True,text=True,env={**os.environ,'BHARATAI_JDK':str(home)})
            self.assertNotEqual(proc.returncode,0)
            self.assertIn('not a full JDK 17 or 21',proc.stderr)

    def test_java22_rejected(self):
        tmp,home=self._fake_jdk(22)
        with tmp:
            proc=subprocess.run(['bash','-c',f'source "{SELECT}"; bharatai_select_jdk'],
                                capture_output=True,text=True,env={**os.environ,'BHARATAI_JDK':str(home)})
            self.assertNotEqual(proc.returncode,0)

    def test_explicit_gradle_java_home(self):
        build=(ROOT/'build_android.sh').read_text()
        bootstrap=(ROOT/'bootstrap_gradle.sh').read_text()
        self.assertIn('-Dorg.gradle.java.home="$JAVA_HOME"', build)
        self.assertIn('-Dorg.gradle.java.home="$JAVA_HOME"', bootstrap)
        self.assertIn('--no-daemon', build)

    def test_gradle_agp_versions(self):
        self.assertIn('8.6.1',(ROOT/'build.gradle.kts').read_text())
        self.assertIn('gradle-8.7',(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text())

    def test_bootstrap_independent_of_agp(self):
        script=(ROOT/'bootstrap_gradle.sh').read_text()
        self.assertIn('wrapper-bootstrap',script)
        self.assertIn('wrapper --gradle-version 8.7',script)

if __name__ == '__main__': unittest.main()
