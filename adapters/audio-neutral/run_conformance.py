"""Build the real JVM consumer, run synthetic cases and compile snapshot shaders.

All generated output stays in ignored local/. No microphone, Android runtime,
graphics context, device, upload, renderer or optical transport is initialized.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MODULE = Path(__file__).resolve().parent
MAIN = 'io.github.mesmerprism.questcompositor.audioneutral.example.AudioNeutralExample'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--supplier', type=Path, required=True)
    parser.add_argument('--gradle-home', type=Path, required=True)
    parser.add_argument('--compiler', type=Path, required=True)
    parser.add_argument('--output-root', type=Path, default=ROOT / 'local/audio-conformance')
    options = parser.parse_args()
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    output_root = options.output_root.resolve()
    if not output_root.is_relative_to((ROOT / 'local').resolve()):
        raise SystemExit('Output must be under ignored repository local/')
    out = output_root / uuid.uuid4().hex
    out.mkdir(parents=True)
    commands = []

    def run(command):
        result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True,
                                text=True, creationflags=flags)
        commands.append({'command':list(map(str, command)), 'exit_code':result.returncode,
                         'stdout':result.stdout, 'stderr':result.stderr})
        if result.returncode:
            (out / 'FAILED.json').write_text(json.dumps(commands, indent=2), encoding='utf-8')
            raise SystemExit(result.stdout + result.stderr)
        return result.stdout

    supplier = options.supplier.resolve()
    pins = json.loads((MODULE / 'SYN-SUPPLIER.json').read_text())
    for row in pins['files']:
        if digest(supplier / row['path']) != row['sha256']:
            raise SystemExit('External supplier input differs from its public pin')
    java = shutil.which('java')
    if not java:
        raise SystemExit('Select JDK17 java on PATH')
    launcher = options.gradle_home.resolve() / 'lib/gradle-gradle-cli-main-9.4.1.jar'
    if not launcher.is_file():
        raise SystemExit('Select an installed Gradle9.4.1 distribution')
    compiler = options.compiler.resolve()
    if compiler.stem.lower() != 'glslc' or not compiler.is_file():
        raise SystemExit('Select an installed glslc compiler')
    # Same Java launcher as the standard Gradle distribution script; no shell.
    build = run([java, '-Djava.awt.headless=true', '-jar', launcher,
                 '--offline', '--no-daemon', '--console=plain', '--max-workers=1',
                 '--project-cache-dir', ROOT / 'local/audio-gradle-cache', '-p', MODULE,
                 '-Pkotlin.compiler.execution.strategy=in-process',
                 '-PsynedelicaSource=' + str(supplier), 'test', 'installDist'])
    (out / 'gradle.log').write_text(build, encoding='utf-8')
    reports = list((ROOT / 'local/audio-neutral-gradle/test-results/test').glob('TEST-*.xml'))
    if not reports:
        raise SystemExit('Ordinary Gradle test report missing')
    totals = {'tests':0, 'failures':0, 'errors':0, 'skipped':0}
    for report in reports:
        for key in totals:
            totals[key] += int(ET.parse(report).getroot().attrib.get(key, '0'))
    if totals['tests'] < 12 or totals['failures'] or totals['errors'] or totals['skipped']:
        raise SystemExit('The real consumer test cohort did not pass')
    # Explicit file loader: never rely on ambient tools resolution/PYTHONPATH.
    harness_path = ROOT / 'tools/check_shaders.py'
    spec = importlib.util.spec_from_file_location('audio_snapshot_shader_harness', harness_path)
    harness = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    vertex = out / 'snapshot.vert'
    vertex.write_text(harness.assemble('vertex', 'fullscreen.vert.glsl'), encoding='utf-8')
    cp = str(ROOT / 'local/audio-neutral-gradle/install/QuestCompositorAudioNeutral/lib/*')
    cases = []
    for name in ('fresh','blue','expired','generation-change','stopped'):
        defines = run([java, '-Djava.awt.headless=true', '-cp', cp, MAIN, name])
        expected_mode = '1' if name in ('fresh','blue') else '0'
        if '#define LAB_RGB_MODE ' + expected_mode + '\n' not in defines:
            raise SystemExit('Synthetic consumer outcome differs: ' + name)
        fragment = out / (name + '.frag')
        fragment.write_text(harness.assemble('fragment', 'clean-rgb.frag.glsl', defines), encoding='utf-8')
        for source in (vertex, fragment):
            run([compiler, '--target-env=opengl', '-fauto-map-locations', '-fauto-bind-uniforms',
                 source, '-o', out / (name + source.suffix + '.spv')])
        cases.append({'case':name,'defines':defines,'fragment_sha256':digest(fragment),
                      'stage_compilation':'pass','mode':expected_mode})
    record = {'schema':'quest.compositor.audio-neutral.conformance.v1',
              'qualification':'JVM consumer/tests and shader stages only; no link/GPU/native/runtime claim',
              'supplier':pins,'tests':totals,'cases':cases,'commands':commands,
              'shader_body_sha256':digest(ROOT / 'examples/clean-rgb.frag.glsl'),
              'harness_file':str(harness_path),'harness_sha256':digest(harness_path),
              'compiler_version':run([compiler,'--version']), 'gui_or_capture_started':False}
    inputs = [MODULE / 'build.gradle.kts', MODULE / 'settings.gradle.kts', MODULE / 'SYN-SUPPLIER.json',
              MODULE / 'run_conformance.py', ROOT / 'examples/audio-reactive/AudioNeutralExample.kt',
              *sorted((MODULE / 'src').rglob('*.kt')), ROOT / 'examples/clean-rgb.frag.glsl',
              ROOT / 'examples/fullscreen.vert.glsl', ROOT / 'tests/fixtures/vertex-prefix.glsl',
              ROOT / 'tests/fixtures/fragment-prefix.glsl']
    record['source_inputs'] = [{'path':path.relative_to(ROOT).as_posix(),'sha256':digest(path)} for path in inputs]
    with (out / 'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(json.dumps(record,indent=2)+'\n')
    print(json.dumps({'tests':totals,'compiled_snapshot_cases':len(cases),
                      'receipt':str(out / 'RESULT.json'),'scope':record['qualification']},indent=2))


if __name__ == '__main__':
    main()
