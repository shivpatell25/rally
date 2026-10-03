#!/usr/bin/env python3
"""Verify an installed optimized update without relying on removed test libraries.

Run after UpdateMigrationDeviceTest has recorded the before-update marker and
Android has installed the migration APK. Credentials use the same four RALLY_
environment variables as the release build. No private preference values leave
the device. Only the temporary verification package is removed on completion.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = 'com.shiv.rally.migrationverification'
parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--device', required=True)
args = parser.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home()/'Library/Android/sdk'))))
build_tools = sdk/'build-tools/34.0.0'
android = sdk/'platforms/android-34/android.jar'
adb = [str(sdk/'platform-tools/adb'), '-s', args.device]
java = Path(os.environ.get('JAVA_HOME') or subprocess.check_output(['/usr/libexec/java_home', '-v', '17'], text=True).strip())
names = ['RALLY_KEYSTORE_PATH', 'RALLY_KEYSTORE_PASSWORD', 'RALLY_KEY_ALIAS', 'RALLY_KEY_PASSWORD']
if not all(os.environ.get(name) for name in names):
    raise SystemExit('Provide the four production signing environment variables.')

def run(command):
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True,
                            env=dict(os.environ, JAVA_HOME=str(java)))
    if result.returncode:
        raise SystemExit(result.stderr or result.stdout or 'Verification command failed.')
    return result.stdout

metadata = run([build_tools/'aapt', 'dump', 'badging', args.apk])
match = re.search(r"package: name='com.shiv.spatelorts' versionCode='(\d+)' versionName='([^']+)'", metadata)
if not match:
    raise SystemExit('Expected a Rally Android APK.')
version_code, version_name = match.groups()
installed = False
try:
    with tempfile.TemporaryDirectory(prefix='rally-native-verifier-') as temporary:
        work = Path(temporary)
        classes, dex = work/'classes', work/'dex'
        classes.mkdir(); dex.mkdir()
        source = ROOT/'scripts/android-update-check'
        run([java/'bin/javac', '-source', '8', '-target', '8', '-classpath', android,
             '-d', classes, source/'MigrationVerifier.java'])
        run([build_tools/'d8', '--lib', android, '--output', dex, *classes.rglob('*.class')])
        unsigned, signed = work/'unsigned.apk', work/'verifier.apk'
        run([build_tools/'aapt', 'package', '-f', '-M', source/'AndroidManifest.xml',
             '-I', android, '-F', unsigned])
        with zipfile.ZipFile(unsigned, 'a') as archive:
            archive.write(dex/'classes.dex', 'classes.dex')
        run([build_tools/'apksigner', 'sign', '--debuggable-apk-permitted', 'false',
             '--ks', os.environ[names[0]], '--ks-key-alias', os.environ[names[2]],
             '--ks-pass', 'env:'+names[1], '--key-pass', 'env:'+names[3], '--out', signed, unsigned])
        run([*adb, 'install', '--no-incremental', '-r', signed])
        installed = True
        output = run([*adb, 'shell', 'am', 'instrument', '-w',
                      '-e', 'expectedVersionCode', version_code, '-e', 'expectedVersionName', version_name,
                      PACKAGE+'/com.shiv.rally.migration.MigrationVerifier'])
        print(output.strip())
        if 'PASS:' not in output:
            raise SystemExit('The installed update failed data/signing verification.')
finally:
    if installed:
        run([*adb, 'uninstall', PACKAGE])
