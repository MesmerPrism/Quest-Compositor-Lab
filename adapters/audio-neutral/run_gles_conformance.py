"""Headless JVM/API/shader interface verification; no Android or GL execution."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MODULE = Path(__file__).resolve().parent


def reflect_uniforms(path):
    """Read compiler-emitted SPIR-V variables/types, not source-text assertions."""
    data = path.read_bytes()
    words = struct.unpack('<' + 'I' * (len(data) // 4), data)
    if words[0] != 0x07230203:
        raise ValueError('Not SPIR-V')
    names, types, variables = {}, {}, {}
    index = 5
    while index < len(words):
        count, opcode = words[index] >> 16, words[index] & 0xffff
        if count < 1:
            raise ValueError('Invalid instruction')
        operands = words[index+1:index+count]
        if opcode == 5:
            names[operands[0]] = struct.pack('<' + 'I' * (len(operands)-1), *operands[1:]).split(b'\0')[0].decode()
        elif opcode in (21, 22, 23, 32):
            types[operands[0]] = (opcode, operands[1:])
        elif opcode == 59:
            variables[operands[1]] = operands[0]
        index += count
    def typename(identity):
        opcode, value = types.get(identity, (0, ()))
        if opcode == 32:
            return typename(value[1])
        if opcode == 22 and value == (32,):
            return 'float'
        if opcode == 21 and value == (32, 1):
            return 'int'
        if opcode == 23 and typename(value[0]) == 'float':
            return 'vec' + str(value[1])
        return 'other'
    return {names[identity]:typename(kind) for identity,kind in variables.items() if identity in names}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--supplier', required=True, type=Path)
    parser.add_argument('--gradle-home', required=True, type=Path)
    parser.add_argument('--android-jar', required=True, type=Path)
    parser.add_argument('--compiler', required=True, type=Path)
    options = parser.parse_args()
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    out = ROOT / 'local/gles-audio-conformance' / uuid.uuid4().hex
    out.mkdir(parents=True)
    commands = []
    def run(command):
        result = subprocess.run(list(map(str, command)), cwd=ROOT, text=True, capture_output=True, creationflags=flags)
        commands.append({'command':list(map(str, command)), 'exit_code':result.returncode,
                         'stdout':result.stdout, 'stderr':result.stderr})
        if result.returncode:
            (out/'FAILED.json').write_text(json.dumps(commands,indent=2))
            raise SystemExit(result.stdout + result.stderr)
        return result.stdout
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    supplier = options.supplier.resolve()
    pins = json.loads((MODULE/'SYN-SUPPLIER.json').read_text())
    for row in pins['files']:
        if digest(supplier/row['path']) != row['sha256']:
            raise SystemExit('Supplier input differs')
    sdk = options.android_jar.resolve()
    if digest(sdk) != '6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad':
        raise SystemExit('Android API34 SDK pin differs')
    java = shutil.which('java')
    if not java:
        raise SystemExit('Select JDK17')
    launcher = options.gradle_home.resolve()/'lib/gradle-gradle-cli-main-9.4.1.jar'
    compiler = options.compiler.resolve()
    if not launcher.is_file() or compiler.stem.lower() != 'glslc' or not compiler.is_file():
        raise SystemExit('Select Gradle9.4.1 and glslc')
    build = run([java,'-Djava.awt.headless=true','-jar',launcher,'--offline','--no-daemon','--console=plain','--max-workers=1',
                 '--project-cache-dir',ROOT/'local/audio-gradle-cache','-p',MODULE,'-Pkotlin.compiler.execution.strategy=in-process',
                 '-PsynedelicaSource='+str(supplier),'-PandroidJar='+str(sdk),
                 'test','installDist','compileAndroidConsumerKotlin','stageNeutralShaderAssets'])
    (out/'gradle.log').write_text(build)
    totals = dict(tests=0,failures=0,errors=0,skipped=0)
    for path in (ROOT/'local/audio-neutral-gradle/test-results/test').glob('TEST-*.xml'):
        for key in totals:
            totals[key] += int(ET.parse(path).getroot().attrib.get(key,'0'))
    if totals['tests'] < 15 or any(totals[key] for key in ('failures','errors','skipped')):
        raise SystemExit('Affected tests did not pass')
    abi = json.loads((MODULE/'GLES-ABI.json').read_text())
    cp = str(ROOT/'local/audio-neutral-gradle/install/QuestCompositorAudioNeutral/lib/*')
    cases = []
    for effect in ('Identity','BlurHorizontal','BlurVertical','CleanRgb','BlueOnly'):
        body = {'Identity':'identity.frag.glsl','BlurHorizontal':'blur-horizontal.frag.glsl',
                'BlurVertical':'blur-vertical.frag.glsl','CleanRgb':'clean-rgb.frag.glsl','BlueOnly':'clean-rgb.frag.glsl'}[effect]
        reflected = {}
        for stage, filename in (('vertex','fullscreen.vert.glsl'),('fragment',body)):
            source = run([java,'-Djava.awt.headless=true','-cp',cp,
                          'io.github.mesmerprism.questcompositor.audioneutral.example.AudioUniformShaderExample',
                          stage,effect,ROOT/'examples'/filename])
            path = out/(effect + ('.vert' if stage == 'vertex' else '.frag'))
            path.write_text(source)
            binary = path.with_suffix(path.suffix+'.spv')
            run([compiler,'--target-env=opengl','-fauto-map-locations','-fauto-bind-uniforms',path,'-o',binary])
            reflected[stage] = reflect_uniforms(binary)
            required = abi['required_uniforms']['vertex' if stage == 'vertex' else effect]
            for name, expected in required.items():
                if reflected[stage].get(name) != expected:
                    raise SystemExit('Compiled uniform interface differs: '+effect+'/'+stage+'/'+name)
        cases.append({'pass':effect,'compiled_uniform_interface':reflected,'status':'stage-compile/reflection-pass'})
    inputs = [*sorted((MODULE/'src').rglob('*.kt')), MODULE/'build.gradle.kts',MODULE/'GLES-ABI.json',Path(__file__),
              ROOT/'examples/audio-reactive/AudioUniformShaderExample.kt',ROOT/'examples/audio-reactive/android/AudioNeutralSurfaceRenderer.kt',
              *sorted((ROOT/'examples').glob('*.glsl'))]
    record = {'schema':'quest.compositor.gles-audio.source-conformance.v1','tests':totals,
              'test_execution':'UP-TO-DATE exact-result reuse' if '> Task :test UP-TO-DATE' in build else 'executed',
              'android_api_compile':'compileAndroidConsumerKotlin successful; SDK stubs not executed',
              'sdk_sha256':digest(sdk),'supplier':pins,'shader_cases':cases,'commands':commands,
              'compiler':{'sha256':digest(compiler),'version':run([compiler,'--version'])},
              'java':{'sha256':digest(Path(java)),'version':run([java,'--version'])},
              'gradle_launcher_sha256':digest(launcher),
              'inputs':[{'path':path.relative_to(ROOT).as_posix(),'sha256':digest(path)} for path in inputs],
              'scope':'source/JVM/API/compiler interface only; no Android GL execution/link/upload/GPU/readback/display claim',
              'foreground_device_capture_or_mic_started':False}
    with (out/'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(json.dumps(record,indent=2)+'\n')
    print(json.dumps({'tests':totals,'shader_interfaces':len(cases),'android_compile':'pass','receipt':str(out/'RESULT.json'),
                      'scope':record['scope']},indent=2))


if __name__ == '__main__':
    main()
