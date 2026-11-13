#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Only the new controller host suites and API compilation; no sink test rerun or runtime execution."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

ROOT=Path(__file__).resolve().parent
ANDROID_JAR_SHA256='6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad'

def run(argv,environment=None):
    result=subprocess.run(argv,cwd=ROOT,env=environment,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print(result.stdout,end='')
    if result.returncode:raise SystemExit(result.returncode)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sink-root',required=True,type=Path,help='Explicit selected public dependency checkout')
    p.add_argument('--android-jar',required=True,type=Path,help='Explicit pinned API 34 jar; compiled only')
    p.add_argument('--output-root',required=True,type=Path,help='Disposable output outside portable payload')
    a=p.parse_args();out=a.output_root.resolve();sink=a.sink_root.resolve()
    if out==ROOT or ROOT in out.parents:raise SystemExit('Generated output must be outside this portable source closure')
    pins=json.loads((ROOT/'SINK-DEPENDENCY.json').read_text(encoding='utf-8'))
    dependencies=[]
    for entry in pins['files']:
        source=sink/entry['path']
        if hashlib.sha256(source.read_bytes()).hexdigest()!=entry['sha256']:raise SystemExit('Selected sink source differs: '+entry['path'])
        dependencies.append(source)
    jar=a.android_jar.resolve(strict=True)
    if hashlib.sha256(jar.read_bytes()).hexdigest()!=ANDROID_JAR_SHA256:raise SystemExit('Android SDK jar pin differs')
    javac,java=shutil.which('javac'),shutil.which('java')
    if not javac or not java:raise SystemExit('Select installed JDK javac/java on PATH')
    work=out/uuid.uuid4().hex;pure=work/'pure';android=work/'android';temporary=work/'tmp'
    for folder in (pure,android,temporary):folder.mkdir(parents=True)
    pure_dependency=[s for s in dependencies if '/src/main/java/' in s.as_posix()]
    pure_source=sorted((ROOT/'src/main/java').rglob('*.java'));tests=sorted((ROOT/'tests/java').rglob('*.java'))
    run([javac,'--release','8','-Xlint:all','-Werror','-d',str(pure),*map(str,pure_dependency+pure_source+tests)])
    for suite in ('CaptureAttemptTest','FiniteCaptureControllerTest'):
        run([java,'-ea','-cp',str(pure),'io.github.mesmerprism.questcompositor.capturebridge.controller.'+suite])
    env=os.environ.copy();env.update(TMPDIR=str(temporary),TMP=str(temporary),TEMP=str(temporary),PYTHONDONTWRITEBYTECODE='1')
    run([sys.executable,'-B','-m','unittest','discover','-s',str(ROOT/'tests'),'-p','test_controller_plan.py','-v'],env)
    android_source=sorted((ROOT/'src/android/java').rglob('*.java'))
    run([javac,'--release','8','-Xlint:all','-Werror','-cp',str(jar),'-d',str(android),*map(str,dependencies+pure_source+android_source)])
    print('PASS new controller suites and pinned API 34 compile; no Android/vendor/device execution')
    print('PASS exact external sink dependency; unchanged sink tests not rerun')

if __name__=='__main__':main()
