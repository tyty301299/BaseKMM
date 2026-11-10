#!/usr/bin/env python3
"""Install project-local JDK 17 and Android tools from verified official archives.
No global shell profiles, Xcode selection or existing SDK installations are changed.
SDK license acceptance remains interactive in the user's Terminal.
"""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.toolchains'
GOOGLE = 'https://dl.google.com/android/repository/'


def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'SHADOW-Mobile-Setup/1.0'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def download(url, destination, expected, algorithm='sha256'):
    if algorithm not in ('sha256', 'sha1'):
        raise RuntimeError('Unsupported official checksum: ' + algorithm)
    def valid(path):
        digest = hashlib.new(algorithm)
        with path.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        return digest.hexdigest().lower() == expected.lower()
    if destination.exists() and valid(destination):
        return
    temporary = destination.with_suffix(destination.suffix + '.partial')
    try:
        print('Downloading ' + destination.name, flush=True)
        request = urllib.request.Request(url, headers={'User-Agent': 'SHADOW-Mobile-Setup/1.0'})
        with urllib.request.urlopen(request, timeout=60) as response, temporary.open('wb') as stream:
            shutil.copyfileobj(response, stream)
        if not valid(temporary):
            raise RuntimeError('Official checksum mismatch: ' + destination.name)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def local_name(element):
    return element.tag.rsplit('}', 1)[-1]


def descendant(element, name):
    return next(child for child in element.iter() if local_name(child) == name)


def java_is_17(home):
    if not home or not (home / 'bin/javac').is_file():
        return False
    result = subprocess.run([str(home / 'bin/javac'), '-version'], capture_output=True, text=True)
    return result.returncode == 0 and 'javac 17.' in result.stdout + result.stderr


def find_java():
    options = [Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else []
    options.append(TOOLS / 'jdk/Contents/Home')
    result = subprocess.run(['/usr/libexec/java_home', '-v', '17'], capture_output=True, text=True)
    if result.returncode == 0:
        options.append(Path(result.stdout.strip()))
    return next((home for home in options if java_is_17(home)), None)


def install_java():
    arch = 'aarch64' if platform.machine() == 'arm64' else 'x64'
    url = ('https://api.adoptium.net/v3/assets/latest/17/hotspot?'
           'architecture=' + arch + '&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=mac&vendor=eclipse')
    packages = json.loads(fetch(url))
    if not packages:
        raise RuntimeError('No official JDK 17 release found for ' + arch)
    package = packages[0]['binary']['package']
    archive = TOOLS / 'downloads/jdk17.tar.gz'
    download(package['link'], archive, package['checksum'])
    with tempfile.TemporaryDirectory(prefix='jdk-stage-', dir=TOOLS) as directory:
        stage = Path(directory)
        # Validate archive names and link targets before using macOS tar.
        with tarfile.open(archive) as bundle:
            for member in bundle.getmembers():
                target = (stage / member.name).resolve()
                if stage not in target.parents:
                    raise RuntimeError('Unsafe JDK archive member')
                if member.issym() or member.islnk():
                    link = ((target.parent if member.issym() else stage) / member.linkname).resolve()
                    if stage not in link.parents:
                        raise RuntimeError('Unsafe JDK archive link')
                elif not (member.isfile() or member.isdir()):
                    raise RuntimeError('Unsupported JDK archive member')
        subprocess.run(['/usr/bin/tar', '-xzf', str(archive), '-C', directory], check=True)
        candidates = list(stage.glob('*/Contents/Home'))
        if len(candidates) != 1 or not java_is_17(candidates[0]):
            raise RuntimeError('Downloaded archive does not contain a working JDK 17')
        destination = TOOLS / 'jdk'
        if destination.exists():
            raise RuntimeError('Existing .toolchains/jdk is not valid; keep it for inspection before retrying')
        shutil.move(str(candidates[0].parent.parent), str(destination))
    return TOOLS / 'jdk/Contents/Home'


def install_android_tools(sdk):
    manager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
    if manager.is_file():
        return manager
    repository = ET.fromstring(fetch(GOOGLE + 'repository2-3.xml'))
    package = next(item for item in repository.iter()
                   if local_name(item) == 'remotePackage' and item.attrib.get('path') == 'cmdline-tools;latest')
    archive = next(item for item in package.iter() if local_name(item) == 'archive'
                   and descendant(item, 'host-os').text == 'macosx')
    complete = descendant(archive, 'complete')
    relative = descendant(complete, 'url').text.strip()
    if '/' in relative or not relative.endswith('.zip'):
        raise RuntimeError('Unexpected Android command-line tools archive URL')
    checksum = descendant(complete, 'checksum')
    algorithm = checksum.attrib.get('type', 'sha1')
    bundle = TOOLS / 'downloads/android-command-tools.zip'
    download(GOOGLE + relative, bundle, checksum.text.strip(), algorithm)
    with tempfile.TemporaryDirectory(prefix='android-stage-', dir=TOOLS) as directory:
        stage = Path(directory)
        with zipfile.ZipFile(bundle) as source:
            for member in source.infolist():
                target = (stage / member.filename).resolve()
                if stage not in target.parents or (member.external_attr >> 16) & 0o170000 == 0o120000:
                    raise RuntimeError('Unsafe Android archive member')
            source.extractall(stage)
        tools = stage / 'cmdline-tools'
        if not (tools / 'bin/sdkmanager').is_file():
            raise RuntimeError('Android archive does not contain sdkmanager')
        destination = sdk / 'cmdline-tools/latest'
        if destination.exists():
            raise RuntimeError('Existing command-line tools directory is incomplete; inspect before retrying')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(tools), str(destination))
        for executable in (destination / 'bin').iterdir():
            executable.chmod(executable.stat().st_mode | 0o111)
    return manager


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--with-emulator', action='store_true', help='Also install an API 35 image and create Shadow_API_35 AVD')
    args = parser.parse_args()
    if sys.platform != 'darwin':
        raise RuntimeError('This bootstrap targets the project macOS development environment')
    (TOOLS / 'downloads').mkdir(parents=True, exist_ok=True)
    java = find_java() or install_java()
    # Deliberately use a project-local SDK, preserving any existing global installation.
    sdk = TOOLS / 'android-sdk'
    manager = install_android_tools(sdk)
    values = {
        'JAVA_HOME': str(java), 'ANDROID_HOME': str(sdk), 'ANDROID_SDK_ROOT': str(sdk),
        'ANDROID_USER_HOME': str(TOOLS / 'android-user'), 'ANDROID_AVD_HOME': str(TOOLS / 'avd'),
        'GRADLE_USER_HOME': str(TOOLS / 'gradle-cache'), 'KONAN_DATA_DIR': str(TOOLS / 'konan'),
    }
    for key in ['ANDROID_USER_HOME', 'ANDROID_AVD_HOME']:
        Path(values[key]).mkdir(parents=True, exist_ok=True)
    env = os.environ.copy(); env.update(values)
    (TOOLS / 'env.sh').write_text('# Generated by setup_mobile_env.py; local machine only.\n' +
        ''.join('export ' + key + '=' + shlex.quote(value) + '\n' for key, value in values.items()))
    print('Android SDK licenses may require your response in Terminal.', flush=True)
    subprocess.run([str(manager), '--sdk_root=' + str(sdk), '--licenses'], env=env, check=True)
    packages = ['platform-tools', 'platforms;android-35', 'build-tools;35.0.0']
    abi = 'arm64-v8a' if platform.machine() == 'arm64' else 'x86_64'
    image = 'system-images;android-35;google_apis;' + abi
    if args.with_emulator:
        packages += ['emulator', image]
    subprocess.run([str(manager), '--sdk_root=' + str(sdk)] + packages, env=env, check=True)
    properties = ROOT / 'local.properties'
    sdk_value = str(sdk).replace('\\', '\\\\').replace(' ', '\\ ').replace(':', '\\:')
    if not properties.exists():
        properties.write_text('sdk.dir=' + sdk_value + '\n')
    else:
        print('Existing local.properties preserved; ensure sdk.dir points to the SDK you want to use.')
    if args.with_emulator:
        avd = sdk / 'cmdline-tools/latest/bin/avdmanager'
        listed = subprocess.run([str(avd), 'list', 'avd', '-c'], env=env, capture_output=True, text=True, check=True)
        if 'Shadow_API_35' not in listed.stdout.splitlines():
            subprocess.run([str(avd), 'create', 'avd', '-n', 'Shadow_API_35', '-k', image, '-d', 'pixel'],
                           input='no\n', text=True, env=env, check=True)
    print('Toolchain installed. Run: ./tools/build-mobile.sh android (or ios).')


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, ValueError, KeyError, StopIteration, subprocess.CalledProcessError, ET.ParseError) as error:
        print('Setup did not complete: ' + str(error), file=sys.stderr)
        print('Use a Terminal with network access; installed/downloaded files remain under .toolchains for retry.', file=sys.stderr)
        sys.exit(1)
