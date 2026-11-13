"""Closed, inert host profile validation and retained selection plans.

Profiles live directly under ROOT/profiles; shader paths are relative to ROOT.
Plan artifacts live below ROOT/local and contain only portable relative inputs.
No command in this module contacts a device or changes a system property.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re
import sys
from typing import Callable, Literal


STAGE_ROOT = '/data/local/tmp/qcl'
PROFILE_SCHEMA = 'quest.compositor.profile.v1'
PLAN_SCHEMA = 'quest.compositor.selection-plan.v1'
ARTIFACT_SCHEMA = 'quest.compositor.plan-artifact.v1'
COMPATIBILITY_ID = 'experimental-gles-body-es310'
_ID = re.compile(r'[a-z][a-z0-9-]{0,63}\Z')
_SHA = re.compile(r'[0-9a-f]{64}\Z')
_PATH_COMPONENT = re.compile(r'[A-Za-z0-9_-][A-Za-z0-9_.-]*\Z')


@dataclass(frozen=True)
class ShaderPass:
    vertex_path: str
    fragment_path: str
    vertex_sha256: str
    fragment_sha256: str
    filter: Literal['linear', 'nearest']
    width: int | None
    height: int | None


@dataclass(frozen=True)
class Profile:
    schema: Literal['quest.compositor.profile.v1']
    profile_id: str
    backend: Literal['gles-body-es310']
    passes: tuple[ShaderPass, ...]
    compatibility_id: str


@dataclass(frozen=True)
class SelectionPlan:
    schema: Literal['quest.compositor.selection-plan.v1']
    profile_id: str
    source_digest: str
    requested_properties: dict[str, str]
    clear_unused_indices: tuple[int, ...]
    requires_fresh_construction: bool


LoadProfile = Callable[[str], Profile]
MakePlan = Callable[[Profile, str], SelectionPlan]


def _closed(value, fields, label):
    if type(value) is not dict or set(value) != set(fields):
        raise ValueError(f'{label}: exact keys required: {", ".join(fields)}')
    return value


def _text(value, pattern, label):
    if type(value) is not str or pattern.fullmatch(value) is None:
        raise ValueError(f'invalid {label}')
    return value


def _relative(value):
    if type(value) is not str:
        raise ValueError('source path must be a portable relative string')
    parts = value.split('/')
    if not parts or any(_PATH_COMPONENT.fullmatch(part) is None or part in ('.', '..')
                        or part.endswith('.') for part in parts):
        raise ValueError('unsafe relative path')
    # Reject Windows device names, including names with extensions.
    reserved = {'CON', 'PRN', 'AUX', 'NUL', *('COM' + str(i) for i in range(1, 10)),
                *('LPT' + str(i) for i in range(1, 10))}
    if any(part.split('.')[0].upper() in reserved for part in parts):
        raise ValueError('reserved path component')
    return parts


def _safe_path(root: Path, relative: str, *, must_exist=True):
    parts = _relative(relative)
    candidate = root
    for part in parts:
        candidate = candidate / part
        if candidate.is_symlink() or candidate.is_junction():
            raise ValueError('symlinks and junctions are forbidden')
    try:
        candidate.resolve().relative_to(root.resolve())
    except ValueError as exc:
        raise ValueError('path escapes root') from exc
    if must_exist and not candidate.is_file():
        raise ValueError(f'missing file: {relative}')
    return candidate


def _manifest_root(path: Path):
    path = path.absolute()
    if path.parent.name != 'profiles':
        raise ValueError('manifest must live directly below ROOT/profiles')
    root = path.parent.parent
    if root.is_symlink() or root.is_junction():
        raise ValueError('repository root cannot be a symlink or junction')
    _safe_path(root, 'profiles/' + path.name)
    return root


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate JSON key: {key}')
        result[key] = value
    return result


def _read(path: Path):
    try:
        return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=_pairs,
                          parse_constant=lambda value: (_ for _ in ()).throw(ValueError('nonfinite JSON')))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError(f'cannot read JSON: {path.name}') from exc


def _canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True, allow_nan=False)


def _validate_profile(value):
    _closed(value, ('schema', 'profile_id', 'backend', 'passes', 'compatibility_id'), 'profile')
    if value['schema'] != PROFILE_SCHEMA or value['backend'] != 'gles-body-es310':
        raise ValueError('unsupported profile schema/backend')
    _text(value['profile_id'], _ID, 'profile id')
    if value['compatibility_id'] != COMPATIBILITY_ID:
        raise ValueError('unknown compatibility id')
    passes = value['passes']
    if type(passes) is not list or not 1 <= len(passes) <= 5:
        raise ValueError('one through five passes required')
    validated = []
    for shader in passes:
        _closed(shader, ('vertex_path', 'fragment_path', 'vertex_sha256', 'fragment_sha256',
                         'filter', 'width', 'height'), 'shader pass')
        for stage in ('vertex', 'fragment'):
            _relative(shader[stage + '_path'])
            _text(shader[stage + '_sha256'], _SHA, stage + ' sha256')
        if type(shader['filter']) is not str or shader['filter'] not in ('linear', 'nearest'):
            raise ValueError('unknown filter')
        width, height = shader['width'], shader['height']
        if not (width is None and height is None):
            if type(width) is not int or type(height) is not int or not (0 < width <= 2147483647 and 0 < height <= 2147483647):
                raise ValueError('dimensions must both be null or positive int32 values')
        validated.append(ShaderPass(**shader))
    return Profile(value['schema'], value['profile_id'], value['backend'], tuple(validated),
                   value['compatibility_id'])


def _compatibility(root: Path):
    value = _read(_safe_path(root, 'profiles/compatibility.json'))
    _closed(value, ('schema', 'compatibility_id', 'backend', 'maximum_pass_count',
                    'status', 'qualification'), 'compatibility')
    if (value['schema'] != 'quest.compositor.compatibility.v1'
            or value['compatibility_id'] != COMPATIBILITY_ID
            or value['backend'] != 'gles-body-es310'
            or value['status'] != 'experimental' or value['qualification'] != 'source-only'):
        raise ValueError('unsupported compatibility record')
    maximum = value['maximum_pass_count']
    if type(maximum) is not int or not 1 <= maximum <= 5:
        raise ValueError('compatibility maximum must be an integer from one to five')
    return maximum


def load_profile(path: str) -> Profile:
    """Load a closed profile and verify every referenced source SHA-256."""
    manifest = Path(path)
    root = _manifest_root(manifest)
    profile = _validate_profile(_read(manifest))
    if len(profile.passes) > _compatibility(root):
        raise ValueError('profile exceeds compatibility maximum')
    for shader in profile.passes:
        for stage in ('vertex', 'fragment'):
            source = _safe_path(root, getattr(shader, stage + '_path'))
            if hashlib.sha256(source.read_bytes()).hexdigest() != getattr(shader, stage + '_sha256'):
                raise ValueError(f'{stage} source hash mismatch')
    return profile


def make_plan(profile: Profile, stage_root: str) -> SelectionPlan:
    """Compute requested state only; requires already verified source staging.

    Stage pass N's vertex source as N.v and fragment source as N.f below the
    full digest directory. Compact names keep property values below 92 bytes.
    """
    if type(stage_root) is not str or stage_root != STAGE_ROOT:
        raise ValueError('stage root must be the owned neutral Android root')
    if type(profile) is not Profile or type(profile.passes) is not tuple or any(type(p) is not ShaderPass for p in profile.passes):
        raise ValueError('typed Profile with tuple of ShaderPass required')
    value = asdict(profile)
    value['passes'] = [asdict(shader) for shader in profile.passes]
    _validate_profile(value)
    digest = hashlib.sha256(_canonical(value).encode('utf-8')).hexdigest()
    owned = stage_root + '/' + digest
    requested = {'debug.oculus.postProcess.enable': 'true',
                 'debug.oculus.postprocess.customPrograms': str(len(profile.passes))}
    for index in range(5):
        prefix = 'debug.oculus.postprocess.'
        if index < len(profile.passes):
            shader = profile.passes[index]
            values = {'vertexShader': owned + '/' + str(index) + '.v',
                      'fragmentShader': owned + '/' + str(index) + '.f',
                      'inputWidth': '' if shader.width is None else str(shader.width),
                      'inputHeight': '' if shader.height is None else str(shader.height),
                      'inputFilter': shader.filter}
        else:
            values = dict.fromkeys(('vertexShader', 'fragmentShader', 'inputWidth', 'inputHeight', 'inputFilter'), '')
        requested.update({prefix + key + str(index): value for key, value in values.items()})
    if any(len(value.encode('utf-8')) > 91 for value in requested.values()):
        raise ValueError('requested property exceeds Android value limit')
    return SelectionPlan(PLAN_SCHEMA, profile.profile_id, digest, requested,
                         tuple(range(len(profile.passes), 5)), True)


def _catalog(path: Path):
    root = _manifest_root(path)
    catalog = _read(path)
    _closed(catalog, ('schema', 'profiles'), 'catalog')
    if catalog['schema'] != 'quest.compositor.catalog.v1' or type(catalog['profiles']) is not dict or not catalog['profiles']:
        raise ValueError('invalid catalog')
    profiles = {}
    for identifier, relative in catalog['profiles'].items():
        _text(identifier, _ID, 'catalog profile id')
        parts = _relative(relative)
        if len(parts) != 2 or parts[0] != 'profiles' or not parts[1].endswith('.json'):
            raise ValueError('catalog profile must be profiles/<name>.json')
        profile = load_profile(str(_safe_path(root, relative)))
        if profile.profile_id != identifier:
            raise ValueError('catalog id differs from profile id')
        profiles[identifier] = (relative, profile)
    return root, profiles


def _plan_output(root: Path, output: Path):
    output = output.absolute()
    try:
        relative = output.relative_to(root.absolute()).as_posix()
    except ValueError as exc:
        raise ValueError('output must be below ROOT/local') from exc
    parts = _relative(relative)
    if len(parts) < 2 or parts[0] != 'local' or not output.name.endswith('.json'):
        raise ValueError('output must be a JSON file below ROOT/local')
    _safe_path(root, relative, must_exist=False)
    return output


def _verify(path: Path):
    path = path.absolute()
    local = next((ancestor for ancestor in path.parents
                  if ancestor.name == 'local' and (ancestor.parent / 'profiles').is_dir()), None)
    if local is None:
        raise ValueError('plan artifact must live below ROOT/local')
    root = local.parent
    _plan_output(root, path)
    artifact = _read(path)
    _closed(artifact, ('schema', 'profile_path', 'stage_root', 'plan'), 'plan artifact')
    if artifact['schema'] != ARTIFACT_SCHEMA:
        raise ValueError('unsupported plan artifact')
    parts = _relative(artifact['profile_path'])
    if len(parts) != 2 or parts[0] != 'profiles' or not parts[1].endswith('.json'):
        raise ValueError('artifact profile must be profiles/<name>.json')
    profile = load_profile(str(_safe_path(root, artifact['profile_path'])))
    expected = asdict(make_plan(profile, artifact['stage_root']))
    # Compare canonical JSON, not Python equality: True must never equal 1.
    if _canonical(artifact['plan']) != _canonical(expected):
        raise ValueError('retained plan differs from recomputed source plan')
    return {'verified': True, 'profile_id': profile.profile_id, 'source_digest': expected['source_digest']}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    listing = commands.add_parser('list')
    listing.add_argument('--catalog', required=True, type=Path)
    planning = commands.add_parser('plan')
    planning.add_argument('--catalog', required=True, type=Path)
    planning.add_argument('--profile', required=True)
    planning.add_argument('--output', required=True, type=Path)
    planning.add_argument('--stage-root', default=STAGE_ROOT)
    verify = commands.add_parser('verify')
    verify.add_argument('--plan', required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == 'verify':
            result = _verify(args.plan)
        else:
            root, profiles = _catalog(args.catalog)
            if args.command == 'list':
                result = {'profiles': sorted(profiles)}
            else:
                if args.profile not in profiles:
                    raise ValueError('profile is not in catalog')
                relative, profile = profiles[args.profile]
                plan = make_plan(profile, args.stage_root)
                artifact = {'schema': ARTIFACT_SCHEMA, 'profile_path': relative,
                            'stage_root': args.stage_root, 'plan': asdict(plan)}
                output = _plan_output(root, args.output)
                output.parent.mkdir(parents=True, exist_ok=True)
                # Exclusive creation preserves existing retained evidence.
                with output.open('x', encoding='utf-8', newline='\n') as stream:
                    stream.write(json.dumps(artifact, indent=2, sort_keys=True) + '\n')
                result = {'created': True, 'profile_id': profile.profile_id, 'source_digest': plan.source_digest}
        print(json.dumps(result, sort_keys=True))
        return 0
    except (ValueError, OSError, TypeError) as exc:
        print(json.dumps({'error': str(exc)}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
