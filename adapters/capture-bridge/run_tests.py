#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Host-only real Java contract tests and optional pinned-SDK API compilation."""
import argparse
import pathlib
import shutil
import subprocess
import uuid

ROOT = pathlib.Path(__file__).resolve().parent

def run(argv):
    result = subprocess.run(argv, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, end="")
    if result.returncode:
        raise SystemExit(result.returncode)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=("access", "descriptor", "lifecycle", "all"), default="all")
    parser.add_argument("--android-jar", type=pathlib.Path)
    parser.add_argument("--require-android", action="store_true")
    args = parser.parse_args()
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise SystemExit("Explicitly selected JDK javac/java must be on PATH")
    out = ROOT / "local" / "host-checks" / uuid.uuid4().hex
    pure_out = out / "pure"
    pure_out.mkdir(parents=True)
    suites = {"access": "PublisherAccessTest", "descriptor": "StereoSinkDescriptorTest", "lifecycle": "SinkAdmissionTest"}
    selected = list(suites.values()) if args.suite == "all" else [suites[args.suite]]
    tests = [ROOT / "tests/java/io/github/mesmerprism/questcompositor/capturebridge" / (name + ".java") for name in selected]
    tests.append(ROOT / "tests/java/io/github/mesmerprism/questcompositor/capturebridge/TestSupport.java")
    production = sorted((ROOT / "src/main/java").rglob("*.java"))
    run([javac, "--release", "8", "-Xlint:all", "-Werror", "-d", str(pure_out), *map(str, production + tests)])
    for name in selected:
        run([java, "-ea", "-cp", str(pure_out), "io.github.mesmerprism.questcompositor.capturebridge." + name])
    if args.require_android and not args.android_jar:
        raise SystemExit("--require-android requires an explicitly selected --android-jar")
    if args.android_jar:
        android_jar = args.android_jar.resolve(strict=True)
        if not android_jar.is_file():
            raise SystemExit("Android SDK jar must be a file")
        android_sources = sorted((ROOT / "src/android/java").rglob("*.java"))
        if not android_sources:
            raise SystemExit("No Android contract sources to compile")
        android_out = out / "android"
        android_out.mkdir()
        run([javac, "--release", "8", "-Xlint:all", "-Werror", "-cp", str(android_jar), "-d", str(android_out),
             *map(str, production + android_sources)])
        print("PASS Android API compilation (no Android runtime execution)")
    else:
        print("Android API compilation: NOT RUN (pass --android-jar)")
    print("PASS selected host contract tests")

if __name__ == "__main__":
    main()
