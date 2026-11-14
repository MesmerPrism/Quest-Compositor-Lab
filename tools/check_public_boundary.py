"""Check a reviewed public file closure; this never grants publication rights."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

MANIFEST = 'docs/PROVENANCE.json'
IGNORED_DIRS = {'.git', 'local', '__pycache__', '.superpowers'}
FORBIDDEN_NAME = re.compile(r'(private.effect|raw.headset|raw.device|prepared.final|original2023)', re.I)
MACHINE_PATH = re.compile(r'(?<![A-Za-z0-9])[A-Za-z]:[\\/]|/(?:Users|home)/[A-Za-z0-9]')
ALLOWED_SUFFIXES = {'.md', '.json', '.py', '.ps1', '.glsl', '.txt'}
SPECIAL_NAMES = {'.gitignore', '.gitattributes', 'LICENSE'}
JAVA_SOURCE_PREFIXES = (
    'adapters/capture-bridge/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/',
    'adapters/capture-bridge/src/android/java/io/github/mesmerprism/questcompositor/capturebridge/',
    'adapters/capture-bridge/tests/java/io/github/mesmerprism/questcompositor/capturebridge/',
    'adapters/capture-bridge/controller/src/main/java/io/github/mesmerprism/questcompositor/capturebridge/controller/',
    'adapters/capture-bridge/controller/src/android/java/io/github/mesmerprism/questcompositor/capturebridge/controller/',
    'adapters/capture-bridge/controller/tests/java/io/github/mesmerprism/questcompositor/capturebridge/controller/',
)
JAVA_EXAMPLE = 'examples/wide-stereo-source/IdentitySinkExample.java'
AUDIO_KOTLIN_PREFIXES = (
    'adapters/audio-neutral/src/main/kotlin/io/github/mesmerprism/questcompositor/audioneutral/',
    'adapters/audio-neutral/src/test/kotlin/io/github/mesmerprism/questcompositor/audioneutral/',
)
AUDIO_BUILD_FILES = {'adapters/audio-neutral/build.gradle.kts',
                     'adapters/audio-neutral/settings.gradle.kts'}
AUDIO_EXAMPLE = 'examples/audio-reactive/AudioNeutralExample.kt'
AUDIO_GENERATED_CACHE = 'adapters/audio-neutral/.kotlin'


def check(root: Path) -> list[str]:
    root = root.resolve()
    errors: list[str] = []
    try:
        document = json.loads((root / MANIFEST).read_text(encoding='utf-8'))
        if document['schema'] != 'quest.compositor.provenance.v1':
            raise ValueError('schema')
        entries = document['files']
        if not isinstance(entries, list):
            raise ValueError('files')
    except (OSError, ValueError, KeyError, TypeError) as exc:
        return [f'invalid provenance manifest: {exc}']
    admitted = {}
    for entry in entries:
        try:
            if set(entry) != {'path', 'sha256', 'license', 'origin'}:
                raise ValueError('entry keys')
            name = entry['path']
            if not isinstance(name, str) or not name or '\\' in name:
                raise ValueError('path')
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or str(path) != name or ':' in name:
                raise ValueError('unsafe path')
            if path.parts[0] in IGNORED_DIRS or any(p in IGNORED_DIRS for p in path.parts):
                raise ValueError('ignored path cannot be admitted')
            if name == AUDIO_GENERATED_CACHE or name.startswith(AUDIO_GENERATED_CACHE + '/'):
                raise ValueError('generated audio compiler cache cannot be admitted')
            if name in admitted:
                raise ValueError('duplicate')
            if entry['license'] != 'AGPL-3.0-or-later' or not isinstance(entry['origin'], str) or not entry['origin'].strip():
                raise ValueError('license/origin notice')
            digest = entry['sha256']
            if name == MANIFEST:
                if digest is not None:
                    raise ValueError('self hash must be null')
            elif not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
                raise ValueError('sha256')
            admitted[name] = entry
        except (ValueError, KeyError, TypeError) as exc:
            errors.append(f'invalid admission: {exc}')
    if MANIFEST not in admitted:
        errors.append('manifest requires its own notice (null self hash)')

    found = set()
    def visit(folder: Path):
        for item in sorted(folder.iterdir()):
            name = item.relative_to(root).as_posix()
            if item.is_symlink() or item.is_junction():
                errors.append(f'link forbidden: {name}')
            elif item.is_dir():
                if item.name not in IGNORED_DIRS and name != AUDIO_GENERATED_CACHE:
                    visit(item)
            elif item.is_file():
                if item.suffix == '.pyc':
                    continue
                found.add(name)
                if name not in admitted:
                    errors.append(f'unadmitted file: {name}')
                if FORBIDDEN_NAME.search(name):
                    errors.append(f'excluded payload name: {name}')
                neutral_java = item.suffix == '.java' and (
                    name.startswith(JAVA_SOURCE_PREFIXES) or name == JAVA_EXAMPLE)
                neutral_audio = name in AUDIO_BUILD_FILES or (item.suffix == '.kt' and (
                    name.startswith(AUDIO_KOTLIN_PREFIXES) or name == AUDIO_EXAMPLE))
                if item.name not in SPECIAL_NAMES and item.suffix not in ALLOWED_SUFFIXES and not neutral_java and not neutral_audio:
                    errors.append(f'unsupported public payload: {name}')
                data = item.read_bytes()
                try:
                    content = data.decode('utf-8')
                    if '\x00' in content:
                        raise ValueError('NUL')
                    if MACHINE_PATH.search(content):
                        errors.append(f'machine locator: {name}')
                except (UnicodeDecodeError, ValueError):
                    errors.append(f'non-text payload: {name}')
                if name in admitted and name != MANIFEST:
                    if hashlib.sha256(data).hexdigest() != admitted[name]['sha256']:
                        errors.append(f'hash mismatch: {name}')
    visit(root)
    for name in admitted.keys() - found:
        errors.append(f'admitted file missing: {name}')
    # Ignore rules cannot conceal a file staged for publication.
    if (root / '.git').exists():
        result = subprocess.run(['git', '-C', str(root), 'ls-files', '--stage', '-z'],
                                capture_output=True, check=False)
        if result.returncode:
            errors.append('unable to inspect Git index')
        else:
            for record in result.stdout.decode('utf-8').split('\0'):
                if not record:
                    continue
                metadata, name = record.split('\t', 1)
                mode, object_id, stage = metadata.split(' ')
                if mode not in {'100644', '100755'} or stage != '0':
                    errors.append(f'unsupported index mode/stage: {name}')
                if name not in admitted:
                    errors.append(f'unadmitted index path: {name}')
                else:
                    staged = subprocess.run(['git', '-C', str(root), 'show', ':' + name],
                                            capture_output=True, check=False)
                    if staged.returncode or not (root / name).is_file() or staged.stdout != (root / name).read_bytes():
                        errors.append(f'index bytes differ from reviewed worktree: {name}')
    return errors


if __name__ == '__main__':
    findings = check(Path(__file__).resolve().parents[1])
    for finding in findings:
        print(finding)
    print('public closure: ' + ('FAIL' if findings else 'PASS'))
    sys.exit(bool(findings))
