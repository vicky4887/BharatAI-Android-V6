#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET
p=Path(__file__).resolve().parents[1]
assert (p/'settings.gradle.kts').exists()
manifest=ET.parse(p/'app/src/main/AndroidManifest.xml').getroot()
assert not any(x.attrib.get('{http://schemas.android.com/apk/res/android}name','').endswith('INTERNET') for x in manifest.findall('uses-permission'))
ET.parse(p/'app/src/main/res/xml/file_paths.xml')
appgradle=(p/'app/build.gradle.kts').read_text()
assert 'com.google.mlkit:text-recognition:16.0.1' in appgradle
assert 'play-services-mlkit' not in appgradle
assert 'compileSdk = 35' in appgradle
code=(p/'app/src/main/java/com/bharatai/education/MainActivity.kt').read_text()
for test in ('ActivityResultContracts.TakePicture','ActivityResultContracts.PickVisualMedia','InputImage.fromFilePath', 'QuestionParser.parse','VerifiedSolver.solve'):
 assert test in code,test
print('PASS project structure / XML / local OCR / no internet permission / Compose workflow')
