#!/usr/bin/env python3
"""Build independent Minecraft targets from one immutable source snapshot."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
TARGETS = json.loads((ROOT / 'minecraft-targets.json').read_text())
INPUTS = ['src', 'scripts', 'gradle', 'build.gradle', 'build-named.gradle', 'settings.gradle', 'gradle.properties',
          'gradlew', 'gradlew.bat', 'minecraft-targets.json', 'LICENSE', 'README.md']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--minecraft', nargs='+', choices=TARGETS, required=True)
    parser.add_argument('--jobs', type=int, default=2)
    parser.add_argument('--java-home', type=Path)
    args = parser.parse_args()
    files = []
    for name in INPUTS:
        path = ROOT / name
        files += sorted(p for p in path.rglob('*') if p.is_file() and '__pycache__' not in p.parts) if path.is_dir() else [path]
    checksum = hashlib.sha256()
    for file in sorted(files):
        checksum.update(str(file.relative_to(ROOT)).encode()); checksum.update(b'\0'); checksum.update(file.read_bytes())
    source_hash = checksum.hexdigest()
    base = ROOT / 'build/matrix'; snapshot = base / 'snapshots' / source_hash
    if not snapshot.exists():
        snapshot.mkdir(parents=True)
        for file in files:
            destination = snapshot / file.relative_to(ROOT); destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(file, destination)
        (snapshot / 'source-manifest.json').write_text(json.dumps({'sha256': source_hash, 'files': len(files)}, indent=2) + '\n')
    java_home = args.java_home or Path(os.environ.get('JAVA_HOME', ''))
    if not (java_home / 'bin/java').is_file():
        homes = [home for home in (Path.home() / '.gradle/jdks').glob('*adoptium-21-*') if (home / 'bin/java').is_file()]
        assert homes, 'Set JAVA_HOME or --java-home to JDK 21'
        java_home = homes[0]
    version = next(line.split('=', 1)[1] for line in (snapshot / 'gradle.properties').read_text().splitlines() if line.startswith('version='))

    def build(mc):
        work = base / 'work' / (mc + '-' + source_hash[:16])
        if not work.exists():
            shutil.copytree(snapshot, work)
        log = work / 'build.log'
        build_java = java_home
        if TARGETS[mc]['java'] == 25:
            named_homes = [home for home in (Path.home() / '.gradle/jdks').glob('*adoptium-25-*') if (home / 'bin/java').is_file()]
            assert named_homes, 'A JDK 25 installation is required for named Minecraft builds'
            build_java = named_homes[0]
        with log.open('w') as output:
            result = subprocess.run(['bash', './gradlew', 'build', 'remapSmokeJar', '-PvoltMinecraft=' + mc,
                '--console=plain', '--max-workers=3', '--no-daemon'], cwd=work, env={**os.environ, 'JAVA_HOME': str(build_java)},
                stdout=output, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        receipt = dict(minecraft=mc, version=version, source_sha256=source_hash, exit_code=result.returncode,
            status='compile-failed' if result.returncode else 'compile-verified', log=str(log.relative_to(ROOT)))
        if result.returncode == 0:
            totals = dict(tests=0, failures=0, errors=0, skipped=0)
            for file in (work / 'build/test-results/test').glob('TEST-*.xml'):
                attributes = ET.parse(file).getroot().attrib
                for key in totals: totals[key] += int(attributes.get(key, 0))
            assert totals['tests'] and not totals['failures'] and not totals['errors'], 'Missing or failed matrix tests'
            receipt.update(totals)
            destination = ROOT / 'build/libs'; destination.mkdir(parents=True, exist_ok=True)
            jars = []
            for jar in (work / 'build/libs').glob('*.jar'):
                temporary = destination / (jar.name + '.matrix-tmp')
                shutil.copy2(jar, temporary); temporary.replace(destination / jar.name); jars.append(jar.name)
            receipt['artifacts'] = jars
        (work / 'build-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        return receipt

    results = []
    with ThreadPoolExecutor(max_workers=max(1, min(2, args.jobs))) as pool:
        pending = {pool.submit(build, mc): mc for mc in dict.fromkeys(args.minecraft)}
        for future in as_completed(pending):
            receipt = future.result(); results.append(receipt)
            print(receipt['minecraft'], receipt['status'], 'tests=' + str(receipt.get('tests', 0)), flush=True)
            (base / ('summary-' + source_hash[:16] + '.json')).write_text(json.dumps(results, indent=2) + '\n')
    print('Immutable source snapshot:', source_hash, flush=True)
    if any(row['exit_code'] for row in results):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
