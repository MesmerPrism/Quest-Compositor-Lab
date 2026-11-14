import hashlib
import json
from pathlib import Path
import tempfile
import subprocess
import unittest

from tools.check_public_boundary import check


class BoundaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'docs').mkdir()
        self.files = {'README.md': b'Neutral authored source\n'}
        self.write()

    def write(self):
        entries = []
        for name, data in self.files.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            entries.append(dict(path=name, sha256=hashlib.sha256(data).hexdigest(),
                                license='AGPL-3.0-or-later', origin='authored'))
        entries.append(dict(path='docs/PROVENANCE.json', sha256=None,
                            license='AGPL-3.0-or-later', origin='authored'))
        (self.root / 'docs/PROVENANCE.json').write_text(json.dumps({
            'schema': 'quest.compositor.provenance.v1', 'files': entries}), encoding='utf-8')

    def test_reviewed_closure_passes(self):
        self.assertEqual([], check(self.root))

    def test_audio_kotlin_and_build_inputs_have_narrow_admission(self):
        for name in ('adapters/audio-neutral/src/main/kotlin/io/github/mesmerprism/questcompositor/audioneutral/Mapper.kt',
                     'adapters/audio-neutral/src/test/kotlin/io/github/mesmerprism/questcompositor/audioneutral/MapperTest.kt',
                     'adapters/audio-neutral/build.gradle.kts', 'adapters/audio-neutral/settings.gradle.kts',
                     'examples/audio-reactive/AudioNeutralExample.kt'):
            self.files[name] = b'// reviewed synthetic source fixture\n'
        self.write()
        self.assertEqual([], check(self.root))

    def test_audio_sibling_kotlin_build_scripts_and_binaries_stay_excluded(self):
        for name in ('adapters/audio-neutral/other.gradle.kts', 'foreign/Code.kt',
                     'examples/audio-reactive/Other.kt', 'adapters/audio-neutral/runtime.jar'):
            self.files[name] = b'forbidden even with a notice\n'
        self.write()
        for name in set(self.files) - {'README.md'}:
            self.assertIn('unsupported public payload: ' + name, check(self.root))

    def test_audio_compiler_cache_cannot_be_admitted_or_force_staged(self):
        name = 'adapters/audio-neutral/.kotlin/synthetic-cache.bin'
        target = self.root / name
        target.parent.mkdir(parents=True)
        target.write_bytes(b'generated\x00')
        self.assertEqual([], check(self.root))
        self.files[name] = b'generated\x00'
        self.write()
        self.assertTrue(any('cannot be admitted' in item for item in check(self.root)))
        del self.files[name]
        self.write()
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        subprocess.run(['git', '-C', str(self.root), 'add', '-f', name], check=True)
        self.assertIn('unadmitted index path: ' + name, check(self.root))

    def test_java_is_admitted_only_in_reviewed_capture_source_locations(self):
        for name in ('adapters/capture-bridge/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/Example.java',
                     'adapters/capture-bridge/src/android/java/io/github/mesmerprism/questcompositor/capturebridge/Example.java',
                     'adapters/capture-bridge/tests/java/io/github/mesmerprism/questcompositor/capturebridge/Example.java',
                     'examples/wide-stereo-source/IdentitySinkExample.java'):
            self.files[name] = b'// reviewed synthetic Java fixture\n'
        self.write()
        self.assertEqual([], check(self.root))

    def test_java_elsewhere_and_compiled_classes_remain_excluded(self):
        for name in ('elsewhere/Example.java', 'examples/wide-stereo-source/Other.java',
                     'adapters/capture-bridge/src/main/java/foreign/Example.java',
                     'adapters/capture-bridge/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/Example.class'):
            self.files[name] = b'forbidden even when manifest-listed\n'
        self.write()
        failures = check(self.root)
        for name in set(self.files) - {'README.md'}:
            self.assertIn('unsupported public payload: ' + name, failures)

    def test_capture_java_still_requires_explicit_file_notice(self):
        name = 'adapters/capture-bridge/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/Example.java'
        target = self.root / name
        target.parent.mkdir(parents=True)
        target.write_text('// not reviewed')
        self.assertIn('unadmitted file: ' + name, check(self.root))

    def test_controller_java_requires_exact_neutral_namespace(self):
        for folder in ('src/main/java', 'src/android/java', 'tests/java'):
            name = 'adapters/capture-bridge/controller/' + folder + '/io/github/mesmerprism/questcompositor/capturebridge/controller/Fixture.java'
            self.files[name] = b'// reviewed controller fixture\n'
        self.write()
        self.assertEqual([], check(self.root))

    def test_controller_siblings_binaries_and_unlisted_java_stay_excluded(self):
        namespace = 'adapters/capture-bridge/controller/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/'
        for name in (namespace + 'foreign/Example.java', namespace + 'controller2/Example.java',
                     namespace + 'controller/Example.class'):
            self.files[name] = b'not admitted as public Java\n'
        self.write()
        failures = check(self.root)
        for name in set(self.files) - {'README.md'}:
            self.assertIn('unsupported public payload: ' + name, failures)
        unlisted = namespace + 'controller/Unlisted.java'
        target = self.root / unlisted
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text('// unreviewed')
        self.assertIn('unadmitted file: ' + unlisted, check(self.root))

    def test_unreviewed_file_rejected(self):
        (self.root / 'surprise.txt').write_text('unreviewed')
        self.assertTrue(any('unadmitted' in e for e in check(self.root)))

    def test_changed_bytes_rejected(self):
        (self.root / 'README.md').write_text('changed')
        self.assertTrue(any('hash' in e for e in check(self.root)))

    def test_admission_cannot_allow_private_binary_or_machine_payload(self):
        cases = [('private-effect.glsl', b'void main(){}'),
                 ('raw-headset-receipt.json', b'{}'),
                 ('runtime.apk', b'package'),
                 ('runtime.so', b'ELF'),
                 ('README.md', ('Secret at ' + 'C' + ':' + chr(92) + 'Users' + chr(92) + 'someone').encode()),
                 ('README.md', b'contains\x00binary')]
        for name, data in cases:
            with self.subTest(name=name, data=data):
                for previous in set(self.files) - {'README.md'}:
                    (self.root / previous).unlink(missing_ok=True)
                self.files = {'README.md': b'Neutral authored source\n'}
                self.files[name] = data
                self.write()
                self.assertTrue(check(self.root))

    def test_missing_notice_rejected(self):
        doc = json.loads((self.root / 'docs/PROVENANCE.json').read_text())
        doc['files'][0]['license'] = ''
        (self.root / 'docs/PROVENANCE.json').write_text(json.dumps(doc))
        self.assertTrue(check(self.root))

    def test_manifest_cannot_escape_root(self):
        doc = json.loads((self.root / 'docs/PROVENANCE.json').read_text())
        doc['files'][0]['path'] = '../outside.txt'
        (self.root / 'docs/PROVENANCE.json').write_text(json.dumps(doc))
        self.assertTrue(check(self.root))

    def test_ignored_local_evidence_is_not_public_closure(self):
        (self.root / 'local').mkdir()
        (self.root / 'local/private-effect.glsl').write_bytes(b'local\x00')
        self.assertEqual([], check(self.root))

    def test_index_bytes_must_match_reviewed_worktree(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        (self.root / 'README.md').write_text('unreviewed staged bytes')
        subprocess.run(['git', '-C', str(self.root), 'add', 'README.md'], check=True)
        self.write()
        self.assertTrue(any('index bytes' in e for e in check(self.root)))

    def test_ignored_file_cannot_be_force_staged(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        (self.root / 'local').mkdir()
        (self.root / 'local/receipt.json').write_text('{}')
        subprocess.run(['git', '-C', str(self.root), 'add', '-f', 'local/receipt.json'], check=True)
        self.assertTrue(any('index path' in e for e in check(self.root)))

    def test_index_symlink_mode_is_rejected_even_with_identical_bytes(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.files['README.md'] = b'../../outside-source'
        self.write()
        blob = subprocess.run(['git', '-C', str(self.root), 'hash-object', '-w', '--stdin'],
                              input=self.files['README.md'], capture_output=True, check=True).stdout.decode().strip()
        subprocess.run(['git', '-C', str(self.root), 'update-index', '--add', '--cacheinfo',
                        '120000,' + blob + ',README.md'], check=True)
        self.assertTrue(any('index mode' in e for e in check(self.root)))


if __name__ == '__main__':
    unittest.main()
