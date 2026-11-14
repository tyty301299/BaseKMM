#!/usr/bin/env python3
"""Build the pinned native video SDK when Xcode's device destination is unavailable."""
import argparse
import hashlib
import json
import plistlib
import subprocess
import urllib.request
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--simulator', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
stage = root / '.toolchains/XmaxSDK'
deps = stage / 'deps'
deps.mkdir(parents=True, exist_ok=True)
frameworks = []
for entry in json.loads((root / 'tools/xmax-device-dependencies.json').read_text()):
    archive = deps / (entry['name'] + '.zip')
    if not archive.exists():
        with urllib.request.urlopen(entry['url'], timeout=120) as response, archive.open('wb') as dest:
            while block := response.read(1024 * 1024):
                dest.write(block)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != entry['sha256']:
        raise SystemExit('Dependency checksum mismatch: ' + entry['name'])
    package = deps / (entry['name'] + '.xcframework')
    if not package.exists():
        with zipfile.ZipFile(archive) as zipped:
            zipped.extractall(deps)
    info = plistlib.loads((package / 'Info.plist').read_bytes())
    arch = 'x86_64' if args.simulator else 'arm64'
    variant = 'simulator' if args.simulator else None
    library = next(item for item in info['AvailableLibraries'] if item['SupportedPlatform'] == 'ios'
                   and item.get('SupportedPlatformVariant') == variant and arch in item['SupportedArchitectures'])
    frameworks.append(package / library['LibraryIdentifier'] / library['LibraryPath'])

kind = 'simulator' if args.simulator else 'device'
output = stage / kind / 'XmaxSDK.framework'
module = output / 'Modules/XmaxSDK.swiftmodule'
module.mkdir(parents=True, exist_ok=True)
target = 'x86_64-apple-ios16.0-simulator' if args.simulator else 'arm64-apple-ios16.0'
sdk = subprocess.check_output(['xcrun', '--sdk', 'iphonesimulator' if args.simulator else 'iphoneos', '--show-sdk-path'], text=True).strip()
compiler = subprocess.check_output(['xcrun', '--find', 'swiftc'], text=True).strip()
cmd = [compiler, '-emit-library', '-emit-module', '-parse-as-library', '-module-name', 'XmaxSDK',
       '-swift-version', '5', '-Onone', '-whole-module-optimization', '-target', target, '-sdk', sdk,
       '-module-cache-path', str(stage / 'ModuleCache'), '-emit-module-path', str(module / (arch + '.swiftmodule')),
       '-Xlinker', '-install_name', '-Xlinker', '@rpath/XmaxSDK.framework/XmaxSDK',
       '-o', str(output / 'XmaxSDK')]
for framework in frameworks:
    cmd += ['-F', str(framework.parent), '-framework', framework.stem]
cmd += [str(path) for path in sorted((root / 'iosApp/Vendor/XmaxSDK/Sources').rglob('*.swift'))]
log = stage / (kind + '-build.log')
with log.open('w') as stream:
    result = subprocess.run(cmd, stdout=stream, stderr=stream)
if result.returncode:
    raise SystemExit('Native video SDK build failed; see ' + str(log))
(output / 'Info.plist').write_bytes(plistlib.dumps({
    'CFBundleExecutable': 'XmaxSDK', 'CFBundleIdentifier': 'com.trioxstudios.ski.video-sdk',
    'CFBundleName': 'XmaxSDK', 'CFBundlePackageType': 'FMWK', 'CFBundleVersion': '1',
    'CFBundleShortVersionString': '1.0', 'MinimumOSVersion': '16.0',
    'CFBundleSupportedPlatforms': ['iPhoneSimulator' if args.simulator else 'iPhoneOS'],
}))
(stage / (kind + '-frameworks.json')).write_text(json.dumps([str(p) for p in [*frameworks, output]], indent=2))
print('Native video SDK built:', kind)
