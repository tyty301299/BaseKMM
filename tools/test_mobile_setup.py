import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import setup_mobile_env as setup


class MobileSetupTests(unittest.TestCase):
    def test_verified_download_is_reused_without_network(self):
        with tempfile.TemporaryDirectory() as root:
            archive = Path(root) / 'archive.zip'
            archive.write_bytes(b'verified')
            checksum = hashlib.sha256(b'verified').hexdigest()
            with patch.object(setup.urllib.request, 'urlopen') as network:
                setup.download('https://example.invalid/archive', archive, checksum)
                network.assert_not_called()

    def test_checksum_failure_does_not_publish_archive(self):
        with tempfile.TemporaryDirectory() as root:
            archive = Path(root) / 'archive.zip'
            with patch.object(setup.urllib.request, 'urlopen', return_value=io.BytesIO(b'corrupt')):
                with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
                    setup.download('https://example.invalid/archive', archive, hashlib.sha256(b'correct').hexdigest())
            self.assertFalse(archive.exists())
            self.assertFalse(archive.with_suffix('.zip.partial').exists())

    def test_android_archive_cannot_escape_staging_directory(self):
        metadata = b'''<repository><remotePackage path="cmdline-tools;latest"><archives>
          <archive><host-os>macosx</host-os><complete><url>tools.zip</url>
          <checksum type="sha256">unused</checksum></complete></archive>
          </archives></remotePackage></repository>'''
        with tempfile.TemporaryDirectory() as root:
            tools = Path(root) / 'tools'
            (tools / 'downloads').mkdir(parents=True)
            def malicious_download(url, destination, expected, algorithm):
                with zipfile.ZipFile(destination, 'w') as archive:
                    archive.writestr('../outside.txt', 'must not extract')
            with patch.object(setup, 'TOOLS', tools), patch.object(setup, 'fetch', return_value=metadata), patch.object(setup, 'download', side_effect=malicious_download):
                with self.assertRaisesRegex(RuntimeError, 'Unsafe Android archive'):
                    setup.install_android_tools(tools / 'sdk')
            self.assertFalse((tools / 'outside.txt').exists())

    def test_java_detection_requires_jdk17_not_only_runtime(self):
        with tempfile.TemporaryDirectory() as root:
            home = Path(root)
            self.assertFalse(setup.java_is_17(home))
            (home / 'bin').mkdir()
            (home / 'bin/javac').touch()
            with patch.object(setup.subprocess, 'run') as run:
                run.return_value.returncode = 0
                run.return_value.stdout = 'javac 21.0.1'
                run.return_value.stderr = ''
                self.assertFalse(setup.java_is_17(home))
                run.return_value.stdout = 'javac 17.0.17'
                self.assertTrue(setup.java_is_17(home))


if __name__ == '__main__':
    unittest.main()
