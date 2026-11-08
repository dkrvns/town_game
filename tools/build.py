#!/usr/bin/env python3
"""Build a small, signed offline APK using only Google's SDK and JDK 17+."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
LOCAL = ROOT / '.toolchain'

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def java_home():
    if os.environ.get('JAVA_HOME'):
        return Path(os.environ['JAVA_HOME'])
    candidates = list(LOCAL.glob('jdk-*/Contents/Home')) + list(LOCAL.glob('jdk-*'))
    for p in candidates:
        if (p / 'bin/javac').exists(): return p
    raise SystemExit('Install JDK 17+ and set JAVA_HOME; see README.md.')

jdk = java_home()
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or LOCAL)
bt = sdk / 'build-tools/35.0.0'
if not bt.exists(): bt = LOCAL / 'android-15'
android = sdk / 'platforms/android-35/android.jar'
if not android.exists(): android = LOCAL / 'android-35/android.jar'
build = ROOT / 'build'
build.mkdir(exist_ok=True)
classes = build / 'test-classes'
classes.mkdir(exist_ok=True)
source = ROOT / 'app/src/main'
run(jdk/'bin/javac','-encoding','UTF-8','-d',classes,
    source/'java/com/cloudtown/game/GameState.java',source/'java/com/cloudtown/game/MapCamera.java',source/'java/com/cloudtown/game/DecorCatalog.java',
    ROOT/'tests/GameStateTest.java',ROOT/'tests/MapCameraTest.java',ROOT/'tests/ExpansionTest.java',ROOT/'tests/ProgressionTest.java')
run(jdk/'bin/java','-cp',classes,'GameStateTest')
run(jdk/'bin/java','-cp',classes,'MapCameraTest')
run(jdk/'bin/java','-cp',classes,'ExpansionTest')
run(jdk/'bin/java','-cp',classes,'ProgressionTest')
if '--test' in sys.argv: sys.exit(0)
if not android.exists() or not bt.exists():
    raise SystemExit('Android SDK platform 35 and build-tools 35.0.0 are required; see README.md.')
for name in ('classes','generated','dex'):
    p=build/name
    if p.exists(): shutil.rmtree(p)
    p.mkdir()
run(bt/'aapt2','compile','--dir',source/'res','-o',build/'resources.zip')
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest=ET.parse(source/'AndroidManifest.xml')
manifest.getroot().set('package','com.cloudtown.game')
manifest.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(bt/'aapt2','link','-o',build/'resources.apk','-I',android,
    '--manifest',build/'AndroidManifest.xml',build/'resources.zip',
    '--java',build/'generated','--min-sdk-version','23','--target-sdk-version','35',
    '--version-code','4','--version-name','1.3')
sources = list((source/'java').rglob('*.java')) + list((build/'generated').rglob('*.java'))
run(jdk/'bin/javac','-encoding','UTF-8','--release','8',
    '-classpath',android,'-d',build/'classes',*sources)
run(jdk/'bin/java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8',
    '--lib',android,'--min-api','23','--output',build/'dex',*sorted((build/'classes').rglob('*.class')))
shutil.copy(build/'resources.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
    for dex in (build/'dex').glob('*.dex'): apk.write(dex,dex.name)
run(bt/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
key=LOCAL/'cloudtown-debug.keystore'
key.parent.mkdir(exist_ok=True)
if not key.exists():
    run(jdk/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android',
        '-keypass','android','-alias','cloudtown','-keyalg','RSA','-keysize','2048',
        '-validity','10000','-dname','CN=Cloud Town Personal Build')
output=ROOT/'dist/CloudTown.apk'
output.parent.mkdir(exist_ok=True)
run(jdk/'bin/java','-jar',bt/'lib/apksigner.jar','sign','--ks',key,
    '--ks-pass','pass:android','--key-pass','pass:android','--out',output,build/'aligned.apk')
run(jdk/'bin/java','-jar',bt/'lib/apksigner.jar','verify','--verbose',output)
print(f'APK ready: {output} ({output.stat().st_size / 1024:.0f} KB)')
if '--ui-tests' in sys.argv:
    test_dir=build/'android-tests'
    test_dir.mkdir(exist_ok=True)
    test_classes=test_dir/'classes';test_classes.mkdir(exist_ok=True)
    test_dex=test_dir/'dex';test_dex.mkdir(exist_ok=True)
    run(jdk/'bin/javac','-encoding','UTF-8','--release','8',
        '-classpath',os.pathsep.join(map(str,[android,build/'classes'])),'-d',test_classes,
        *sorted((ROOT/'tests/android').glob('*.java')))
    run(jdk/'bin/java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8',
        '--lib',android,'--classpath',build/'classes','--min-api','23','--output',test_dex,
        *test_classes.rglob('*.class'))
    run(bt/'aapt2','link','-o',test_dir/'unsigned.apk','-I',android,
        '--manifest',ROOT/'tests/android/AndroidManifest.xml')
    with zipfile.ZipFile(test_dir/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
        apk.write(test_dex/'classes.dex','classes.dex')
    run(bt/'zipalign','-f','4',test_dir/'unsigned.apk',test_dir/'aligned.apk')
    run(jdk/'bin/java','-jar',bt/'lib/apksigner.jar','sign','--ks',key,
        '--ks-pass','pass:android','--key-pass','pass:android','--out',test_dir/'CameraTests.apk',test_dir/'aligned.apk')
