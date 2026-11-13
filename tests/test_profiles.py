"""Host-only profile validation and retained-plan integrity tests."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools import compositor_profiles as cp


class ProfileTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'profiles').mkdir()
        (self.root / 'examples').mkdir()
        (self.root / 'local').mkdir()
        self.vertex = b'void main() { gl_Position = vec4(0.0); }\n'
        self.fragment = b'void main() { }\n'
        (self.root / 'examples/fullscreen.vert.glsl').write_bytes(self.vertex)
        (self.root / 'examples/identity.frag.glsl').write_bytes(self.fragment)
        self.compatibility = {
            'schema': 'quest.compositor.compatibility.v1',
            'compatibility_id': 'experimental-gles-body-es310',
            'backend': 'gles-body-es310', 'maximum_pass_count': 5,
            'status': 'experimental', 'qualification': 'source-only',
        }
        self.save('profiles/compatibility.json', self.compatibility)
        self.record = {
            'schema': 'quest.compositor.profile.v1', 'profile_id': 'identity',
            'backend': 'gles-body-es310',
            'compatibility_id': 'experimental-gles-body-es310',
            'passes': [{
                'vertex_path': 'examples/fullscreen.vert.glsl',
                'fragment_path': 'examples/identity.frag.glsl',
                'vertex_sha256': hashlib.sha256(self.vertex).hexdigest(),
                'fragment_sha256': hashlib.sha256(self.fragment).hexdigest(),
                'filter': 'linear', 'width': None, 'height': None,
            }],
        }
        self.save('profiles/identity.json', self.record)
        self.save('profiles/catalog.json', {
            'schema': 'quest.compositor.catalog.v1',
            'profiles': {'identity': 'profiles/identity.json'},
        })

    def save(self, relative, value):
        path = self.root / relative
        path.write_text(json.dumps(value), encoding='utf-8')
        return path

    def load(self, record=None):
        if record is not None:
            self.save('profiles/identity.json', record)
        return cp.load_profile(str(self.root / 'profiles/identity.json'))

    def cli(self, *args):
        return subprocess.run([sys.executable, str(Path(cp.__file__)), *args],
                              capture_output=True, text=True, cwd=self.root)

    def test_typed_profile_and_exact_case_properties(self):
        profile = self.load()
        self.assertIsInstance(profile, cp.Profile)
        self.assertIsInstance(profile.passes[0], cp.ShaderPass)
        plan = cp.make_plan(profile, '/data/local/tmp/qcl')
        self.assertEqual(plan.requested_properties['debug.oculus.postProcess.enable'], 'true')
        self.assertEqual(plan.requested_properties['debug.oculus.postprocess.customPrograms'], '1')
        self.assertEqual(plan.requested_properties['debug.oculus.postprocess.inputFilter0'], 'linear')
        self.assertTrue(plan.requires_fresh_construction)

    def test_closed_schema_and_invalid_scalar_types(self):
        mutations = [dict(extra=True), dict(schema='v2'), dict(backend='vulkan'),
                     dict(profile_id=True), dict(profile_id='../escape'),
                     dict(compatibility_id='unknown'), dict(passes=[]),
                     dict(passes=[self.record['passes'][0]] * 6)]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                record = dict(self.record, **mutation)
                with self.assertRaises(ValueError):
                    self.load(record)

    def test_pass_hash_filter_and_dimension_validation(self):
        mutations = [dict(extra=1), dict(vertex_sha256='bad'),
                     dict(fragment_sha256='0' * 64), dict(filter='cubic'),
                     dict(width=0, height=1), dict(width=-1, height=1),
                     dict(width=True, height=1), dict(width=1.5, height=1),
                     dict(width=1, height=None), dict(width=None, height=1)]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                record = copy.deepcopy(self.record)
                record['passes'][0].update(mutation)
                with self.assertRaises(ValueError):
                    self.load(record)

    def test_rejects_nonportable_paths_and_missing_sources(self):
        drive_absolute = 'C' + ':' + '/source'
        for path in ['/tmp/source', drive_absolute, '../secret', 'examples/../source',
                     'examples\\identity.frag.glsl', 'examples//identity.frag.glsl',
                     'examples/missing.glsl', 'examples/a;echo.glsl', './examples/identity.frag.glsl']:
            with self.subTest(path=path):
                record = copy.deepcopy(self.record)
                record['passes'][0]['fragment_path'] = path
                with self.assertRaises(ValueError):
                    self.load(record)

    def test_symlink_sources_are_rejected_even_inside_root(self):
        link = self.root / 'examples/link.glsl'
        try:
            link.symlink_to(self.root / 'examples/identity.frag.glsl')
        except OSError:
            self.skipTest('host cannot create symlinks')
        self.record['passes'][0]['fragment_path'] = 'examples/link.glsl'
        with self.assertRaises(ValueError):
            self.load(self.record)

    def test_duplicate_json_keys_are_rejected(self):
        path = self.root / 'profiles/identity.json'
        path.write_text(json.dumps(self.record)[:-1] + ',"profile_id":"other"}', encoding='utf-8')
        with self.assertRaises(ValueError):
            self.load()

    def test_compatibility_is_closed_and_limits_passes(self):
        for change in [dict(maximum_pass_count=True), dict(maximum_pass_count=6),
                       dict(maximum_pass_count=0), dict(extra=1),
                       dict(status='supported'), dict(backend='other')]:
            with self.subTest(change=change):
                self.save('profiles/compatibility.json', dict(self.compatibility, **change))
                with self.assertRaises(ValueError):
                    self.load()
        self.save('profiles/compatibility.json', dict(self.compatibility, maximum_pass_count=1))
        self.record['passes'] *= 2
        with self.assertRaises(ValueError):
            self.load(self.record)

    def test_plan_is_deterministic_content_specific_and_rejects_shell_root(self):
        profile = self.load()
        first = cp.make_plan(profile, '/data/local/tmp/qcl')
        self.assertEqual(first, cp.make_plan(profile, '/data/local/tmp/qcl'))
        selector = first.requested_properties['debug.oculus.postprocess.fragmentShader0']
        self.assertEqual(selector, '/data/local/tmp/qcl/' + first.source_digest + '/0.f')
        self.record['passes'][0].update(width=100, height=50)
        second = cp.make_plan(self.load(self.record), '/data/local/tmp/qcl')
        self.assertNotEqual(first.source_digest, second.source_digest)
        for root in ['/tmp/other', '/data/local/tmp/qcl;echo x',
                     '/data/local/tmp/qcl/../escape', True]:
            with self.assertRaises(ValueError):
                cp.make_plan(profile, root)

    def test_replacing_five_or_three_passes_clears_all_inherited_selectors(self):
        for count in (5, 3):
            record = copy.deepcopy(self.record)
            record['passes'] *= count
            prior = cp.make_plan(self.load(record), '/data/local/tmp/qcl')
            replacement = cp.make_plan(self.load(self.record), '/data/local/tmp/qcl')
            effective = dict(prior.requested_properties)
            effective.update(replacement.requested_properties)
            self.assertEqual(replacement.clear_unused_indices, (1, 2, 3, 4))
            for index in range(1, 5):
                for suffix in ('vertexShader', 'fragmentShader', 'inputWidth', 'inputHeight', 'inputFilter'):
                    self.assertEqual(effective[f'debug.oculus.postprocess.{suffix}{index}'], '')

    def test_api_rejects_forged_dataclass_control_types(self):
        profile = self.load()
        from dataclasses import replace
        for forged in [replace(profile, backend='other'), replace(profile, passes=()),
                       replace(profile, passes=(replace(profile.passes[0], width=True, height=1),))]:
            with self.assertRaises(ValueError):
                cp.make_plan(forged, '/data/local/tmp/qcl')

    def test_shader_directory_junctions_are_rejected(self):
        if sys.platform != 'win32':
            self.skipTest('Windows junction fixture')
        junction = self.root / 'linked'
        result = subprocess.run(['powershell', '-NoProfile', '-Command',
            "New-Item -ItemType Junction -Path '" + str(junction) +
            "' -Target '" + str(self.root / 'examples') + "' | Out-Null"],
            capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.record['passes'][0]['fragment_path'] = 'linked/identity.frag.glsl'
        with self.assertRaises(ValueError):
            self.load(self.record)

    def test_dimensions_and_nearest_filter_survive_plan_generation(self):
        self.record['passes'][0].update(width=1024, height=512, filter='nearest')
        plan = cp.make_plan(self.load(self.record), '/data/local/tmp/qcl')
        self.assertEqual(plan.requested_properties['debug.oculus.postprocess.inputWidth0'], '1024')
        self.assertEqual(plan.requested_properties['debug.oculus.postprocess.inputHeight0'], '512')
        self.assertEqual(plan.requested_properties['debug.oculus.postprocess.inputFilter0'], 'nearest')

    def test_every_requested_property_fits_android_value_limit(self):
        plan = cp.make_plan(self.load(), cp.STAGE_ROOT)
        for key, value in plan.requested_properties.items():
            with self.subTest(property=key):
                self.assertLessEqual(len(value.encode('utf-8')), 91)

    def test_verify_rejects_plan_outside_owned_local_and_catalog_traversal(self):
        result = self.cli('plan', '--catalog', str(self.root / 'profiles/catalog.json'),
                          '--profile', 'identity', '--output', str(self.root / 'unowned.json'))
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'unowned.json').exists())
        self.save('profiles/catalog.json', {
            'schema': 'quest.compositor.catalog.v1',
            'profiles': {'identity': '../identity.json'},
        })
        self.assertNotEqual(self.cli('list', '--catalog', str(self.root / 'profiles/catalog.json')).returncode, 0)

    def test_nested_local_named_directory_still_verifies(self):
        output = self.root / 'local/nested/local/plan.json'
        result = self.cli('plan', '--catalog', str(self.root / 'profiles/catalog.json'),
                          '--profile', 'identity', '--output', str(output))
        self.assertEqual(result.returncode, 0, result.stderr)
        result = self.cli('verify', '--plan', str(output))
        self.assertEqual(result.returncode, 0, result.stderr)

    def create_plan(self):
        output = self.root / 'local/plan.json'
        result = self.cli('plan', '--catalog', str(self.root / 'profiles/catalog.json'),
                          '--profile', 'identity', '--output', str(output))
        self.assertEqual(result.returncode, 0, result.stderr)
        return output

    def test_cli_list_plan_verify_and_exclusive_output(self):
        listing = self.cli('list', '--catalog', str(self.root / 'profiles/catalog.json'))
        self.assertEqual(listing.returncode, 0, listing.stderr)
        self.assertEqual(json.loads(listing.stdout)['profiles'], ['identity'])
        output = self.create_plan()
        before = output.read_bytes()
        verified = self.cli('verify', '--plan', str(output))
        self.assertEqual(verified.returncode, 0, verified.stderr)
        self.assertEqual(json.loads(verified.stdout)['verified'], True)
        duplicate = self.cli('plan', '--catalog', str(self.root / 'profiles/catalog.json'),
                             '--profile', 'identity', '--output', str(output))
        self.assertNotEqual(duplicate.returncode, 0)
        self.assertEqual(output.read_bytes(), before)

    def test_verify_rejects_plan_source_and_reference_tampering(self):
        output = self.create_plan()
        original = json.loads(output.read_text())
        for change in ('properties', 'digest', 'fresh', 'traversal', 'extra', 'bool-index'):
            artifact = copy.deepcopy(original)
            if change == 'properties':
                artifact['plan']['requested_properties']['debug.oculus.postprocess.customPrograms'] = '5'
            elif change == 'digest':
                artifact['plan']['source_digest'] = '0' * 64
            elif change == 'fresh':
                artifact['plan']['requires_fresh_construction'] = False
            elif change == 'traversal':
                artifact['profile_path'] = '../outside.json'
            elif change == 'extra':
                artifact['plan']['extra'] = 'tampered'
            else:
                artifact['plan']['clear_unused_indices'][0] = True
            self.save('local/plan.json', artifact)
            result = self.cli('verify', '--plan', str(output))
            self.assertNotEqual(result.returncode, 0, change)
        self.save('local/plan.json', original)
        (self.root / 'examples/identity.frag.glsl').write_bytes(b'tampered')
        self.assertNotEqual(self.cli('verify', '--plan', str(output)).returncode, 0)


if __name__ == '__main__':
    unittest.main()
