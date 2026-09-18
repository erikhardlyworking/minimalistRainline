#!/usr/bin/env python3
"""Export real Android screens on an empty emulator; restore its display afterwards.
Build assembleDebug and assembleDebugAndroidTest first. No network/location fixtures.
"""
import argparse
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_HOME', Path.home() / 'Android/Sdk')) / 'platform-tools/adb'))
parser.add_argument('--output', type=Path, default=ROOT / 'output/play-captures')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('Store captures are restricted to an empty local emulator.')

def adb(*command, binary=False):
    return subprocess.check_output([args.adb, '-s', args.serial, *command], text=not binary)

def setting(key):
    return adb('shell', 'settings', 'get', 'system', key).strip()

def restore_setting(key, value):
    if value == 'null':
        adb('shell', 'settings', 'delete', 'system', key)
    else:
        adb('shell', 'settings', 'put', 'system', key, value)

if not adb('shell', 'getprop', 'ro.product.model').strip().startswith('sdk_gphone'):
    parser.error('The selected device is not a supported Android emulator.')
size = re.search(r'Override size: (\S+)', adb('shell', 'wm', 'size'))
density = re.search(r'Override density: (\S+)', adb('shell', 'wm', 'density'))
rotation, auto = setting('user_rotation'), setting('accelerometer_rotation')
locales = json.loads((ROOT / 'play/listings.json').read_text())
names = ['icon.png'] + [f'{locale}/{name}.png' for locale in locales for name in
                       ['01-forecast', '02-appearance', '03-rainfall', '04-time-axis', 'feature-graphic']]
try:
    print(adb('install', '-r', '--user', '0', str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk')).strip())
    print(adb('install', '-r', '-t', '--user', '0', str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).strip())
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    adb('shell', 'wm', 'size', '1080x1920')
    adb('shell', 'wm', 'density', '480')
    result = adb('shell', 'am', 'instrument', '--user', '0', '-w', '-r', '-e', 'store', 'true',
                 'app.rainline.test/app.rainline.SmokeInstrumentation')
    print(result)
    if 'INSTRUMENTATION_CODE: -1' not in result or '20 native screenshots' not in result:
        raise RuntimeError('Native export failed; no assets copied')
    archive_data = adb('exec-out', 'run-as', 'app.rainline', 'tar', '-cf', '-', '-C', 'files', 'store-captures', binary=True)
    with tarfile.open(fileobj=io.BytesIO(archive_data)) as archive:
        for name in names:
            target = args.output / name
            target.parent.mkdir(parents=True, exist_ok=True)
            # Copy only expected regular files; never extract arbitrary archive paths.
            member = archive.getmember('store-captures/' + name)
            if not member.isfile():
                raise RuntimeError(f'Not a regular file: {name}')
            target.write_bytes(archive.extractfile(member).read())
    print(f'Exported {len(names)} assets to {args.output}')
    adb('shell', 'run-as', 'app.rainline', 'rm', '-f', *['files/store-captures/' + name for name in names])
    adb('shell', 'run-as', 'app.rainline', 'rmdir', *['files/store-captures/' + locale for locale in locales], 'files/store-captures')
finally:
    adb('shell', 'wm', 'size', size.group(1) if size else 'reset')
    adb('shell', 'wm', 'density', density.group(1) if density else 'reset')
    restore_setting('user_rotation', rotation)
    restore_setting('accelerometer_rotation', auto)
