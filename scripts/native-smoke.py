#!/usr/bin/env python3
"""Run published-namespace JARs in an isolated offline Minecraft instance."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import time
import urllib.request
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parent.parent
TARGETS = json.loads((ROOT / 'minecraft-targets.json').read_text())
CACHE = ROOT / '.tools/native-qa'
GRADLE = Path.home() / '.gradle/caches'


def digest(file, algorithm='sha1'):
    with Path(file).open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def fetch(url, destination, expected=None):
    destination = Path(destination)
    if destination.is_file() and (not expected or digest(destination) == expected):
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + '.download')
    request = urllib.request.Request(url, headers={'User-Agent': 'Volt-native-QA/0.7.0'})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response, temporary.open('wb') as output:
                shutil.copyfileobj(response, output)
            break
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            temporary.unlink(missing_ok=True)
            if attempt == 2:
                raise
            print('Retrying official dependency download: ' + destination.name, flush=True)
    if expected and digest(temporary) != expected:
        temporary.unlink()
        raise RuntimeError('Dependency checksum mismatch: ' + destination.name)
    temporary.replace(destination)
    return destination


def metadata(url, filename):
    return json.loads(fetch(url, CACHE / filename).read_text())


def artifact(item, external_cache):
    relative = Path(item['path'])
    assert not relative.is_absolute() and '..' not in relative.parts
    filename = relative.name
    candidates = []
    if external_cache:
        candidates.append(external_cache / relative)
    parts = relative.parts
    if len(parts) >= 4:
        module = GRADLE / 'modules-2/files-2.1' / '.'.join(parts[:-3]) / parts[-3] / parts[-2]
        candidates += list(module.glob('*/' + filename))
    for candidate in candidates:
        if candidate.is_file() and (not item.get('sha1') or digest(candidate) == item['sha1']):
            return candidate
    return fetch(item['url'], CACHE / 'dependencies' / relative, item.get('sha1'))


def allowed(library):
    result = not library.get('rules')
    for rule in library.get('rules', []):
        os_rule = rule.get('os', {})
        wanted = {'Linux': 'linux', 'Darwin': 'osx', 'Windows': 'windows'}[platform.system()]
        if os_rule.get('name', wanted) != wanted:
            continue
        if 'arch' in os_rule and not re.fullmatch(os_rule['arch'], platform.machine()):
            continue
        if 'version' in os_rule and not re.search(os_rule['version'], platform.release()):
            continue
        result = rule['action'] == 'allow'
    return result


def assets(game, existing):
    info = game['assetIndex']
    index_file = fetch(info['url'], CACHE / 'assets/indexes' / (info['id'] + '.json'), info['sha1'])
    index = json.loads(index_file.read_text())
    def prepare(entry):
        value = entry['hash']; assert re.fullmatch('[a-f0-9]{40}', value)
        relative = Path(value[:2]) / value
        target = CACHE / 'assets/objects' / relative
        if target.is_file():
            return
        if target.is_symlink():
            target.unlink()
        for directory in [existing, GRADLE / 'fabric-loom/assets']:
            if directory and (directory / 'objects' / relative).is_file():
                target.parent.mkdir(parents=True, exist_ok=True)
                try: target.symlink_to((directory / 'objects' / relative).resolve())
                except FileExistsError: pass
                return
        fetch('https://resources.download.minecraft.net/' + value[:2] + '/' + value, target, value)
    unique = {entry['hash']: entry for entry in index['objects'].values()}
    print(f'Preparing {len(unique)} official asset objects (index {info["id"]})', flush=True)
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(prepare, unique.values()))
    return CACHE / 'assets', info['id']


def acquire_runtime_lock():
    """Fullscreen tests share the display/GPU; never run two clients together."""
    if os.name == 'nt':
        return None
    import fcntl
    CACHE.mkdir(parents=True, exist_ok=True)
    guard = (CACHE / 'runtime.lock').open('a')
    print('Waiting for the isolated fullscreen test slot', flush=True)
    fcntl.flock(guard, fcntl.LOCK_EX)
    return guard


def stage_crash_fixture(process, log, run, timeout):
    """Terminate only our isolated test process group after a durable journal exists."""
    assert os.name != 'nt', 'Controlled process-group crash QA currently requires Unix'
    deadline = time.monotonic() + timeout
    while process.poll() is None:
        if 'VOLT_CRASH_FIXTURE ready' in log.read_text():
            for file in sorted((run / 'volt/cache').glob('session-*/long-*.json'), key=lambda p: p.stat().st_mtime, reverse=True):
                data = json.loads(file.read_text())
                if data['end'] - data['start'] >= 4 and data.get('markers'):
                    fixture = dict(journal=str(file.relative_to(ROOT)), clip_id=data['id'], expected_seconds=data['end'] - data['start'])
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=10)
                    assert file.is_file(), 'The abrupt stop lost its journal'
                    return fixture
        if time.monotonic() > deadline:
            raise subprocess.TimeoutExpired(process.args, timeout)
        time.sleep(.25)
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--minecraft', choices=TARGETS, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--smoke-jar', type=Path, required=True)
    parser.add_argument('--mode', choices=['recording', 'session', 'library', 'crash'], default='recording')
    parser.add_argument('--loader', help='Defaults to the declared minimum loader')
    parser.add_argument('--java', type=Path)
    parser.add_argument('--dependency-cache', type=Path)
    parser.add_argument('--assets', type=Path)
    parser.add_argument('--modpack', type=Path, help='Optional matching-version mod JAR folder; no user configs or worlds are copied')
    parser.add_argument('--timeout', type=int, default=180)
    args = parser.parse_args()
    runtime_guard = acquire_runtime_lock()
    mc = args.minecraft; target = TARGETS[mc]; loader = args.loader or target['loader_minimum']
    print(f'Preparing Minecraft {mc}, Fabric Loader {loader}, mode {args.mode}', flush=True)
    manifest = metadata('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', 'manifest.json')
    entry = next(item for item in manifest['versions'] if item['id'] == mc)
    game = metadata(entry['url'], mc + '.json')
    info = game['downloads']['client']
    cached = GRADLE / 'fabric-loom' / mc / 'minecraft-client.jar'
    client = cached if cached.is_file() and digest(cached) == info['sha1'] else fetch(info['url'], CACHE / (mc + '-client.jar'), info['sha1'])
    cp = [client]
    libraries = [item for item in game['libraries'] if allowed(item) and 'artifact' in item.get('downloads', {}) and not item['name'].startswith('org.ow2.asm:')]
    with ThreadPoolExecutor(max_workers=8) as pool:
        cp += list(pool.map(lambda item: artifact(item['downloads']['artifact'], args.dependency_cache), libraries))
    profile = metadata(f'https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json', f'{mc}-loader-{loader}.json')
    for item in profile['libraries']:
        group, name, version = item['name'].split(':')
        relative = group.replace('.', '/') + f'/{name}/{version}/{name}-{version}.jar'
        cp.append(artifact({'path': relative, 'url': item.get('url', 'https://maven.fabricmc.net/') + relative}, args.dependency_cache))
    api_version = target['fabric_api']
    relative = f'net/fabricmc/fabric-api/fabric-api/{api_version}/fabric-api-{api_version}.jar'
    api = artifact({'path': relative, 'url': 'https://maven.fabricmc.net/' + relative}, args.dependency_cache)
    assets_dir, index = assets(game, args.assets)
    run = ROOT / 'run/native' / mc; mods = run / 'mods'; mods.mkdir(parents=True, exist_ok=True)
    # Initialize only our isolated test instance. Avoid an onboarding screen and
    # a second font/resource reload racing the test's first menu interaction.
    options_file = run / 'options.txt'
    options = dict(line.split(':', 1) for line in options_file.read_text().splitlines() if ':' in line) if options_file.is_file() else {}
    options.update(lang='tr_tr', onboardAccessibility='false')
    options_file.write_text(''.join(key + ':' + value + '\n' for key, value in options.items()))
    for previous in mods.glob('*.jar'):
        previous.unlink()
    if args.modpack:
        for file in args.modpack.glob('*.jar'):
            if not file.name.startswith('volt-'):
                shutil.copy2(file, mods / file.name)
    jar = args.jar.resolve(); original_hash = digest(jar, 'sha512')
    shutil.copy2(jar, mods / jar.name)
    shutil.copy2(args.smoke_jar, mods / args.smoke_jar.name)
    shutil.copy2(api, mods / api.name)
    assert digest(mods / jar.name, 'sha512') == original_hash
    with zipfile.ZipFile(jar) as archive:
        meta = json.loads(archive.read('fabric.mod.json'))
        assert meta['depends']['minecraft'].removeprefix('~') == mc
    java = args.java
    if java is None:
        homes = [home for home in (Path.home() / '.gradle/jdks').glob(f'*adoptium-{target["java"]}-*') if (home / 'bin/java').is_file()]
        java = homes[0] / 'bin/java' if homes else Path(os.environ.get('JAVA_HOME', '/usr')) / 'bin/java'
    settings = ['-Dvolt.fullscreen=true', '-Dvolt.secondaryDisplay=true', '-Dvolt.uiScale=3', '-Dvolt.testFrameLimit=75', '-Dvolt.testFfmpeg=ffmpeg']
    if args.mode == 'session': settings.append('-Dvolt.sessionSafetyTest=true')
    if args.mode == 'crash': settings.append('-Dvolt.crashRecoveryFixture=true')
    if args.mode == 'library': settings += ['-Dvolt.uiOnly=true', '-Dvolt.librarySafetyTest=true', '-Dvolt.strictUiTest=true']
    command = [str(java), '-Xms256m', '-Xmx1536m', '-XX:ActiveProcessorCount=4', '-Dfabric.log.disableAnsi=true', *settings, '-cp', os.pathsep.join(map(str, cp)),
        'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', 'VoltTester', '--version', mc,
        '--gameDir', str(run), '--assetsDir', str(assets_dir), '--assetIndex', index,
        '--accessToken', '0', '--uuid', '00000000000000000000000000000001', '--userType', 'legacy', '--versionType', 'release',
        '--width', '1280', '--height', '800']
    before = set((run / 'volt/clips').glob('*.mp4'))
    log = run / ('native-' + args.mode + '.log')
    environment = dict(os.environ, SDL_VIDEODRIVER='x11')
    print('Launching isolated fullscreen client; log: ' + str(log.relative_to(ROOT)), flush=True)
    with log.open('w') as output:
        process = subprocess.Popen(command, cwd=run, stdout=output, stderr=subprocess.STDOUT, env=environment, start_new_session=True)
        fixture = None
        try:
            if args.mode == 'crash': fixture = stage_crash_fixture(process, log, run, args.timeout)
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGTERM)
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: os.killpg(process.pid, signal.SIGKILL); process.wait()
            raise RuntimeError('Native smoke test timed out; see ' + str(log)) from None
    if args.mode == 'crash':
        assert fixture and code == -signal.SIGKILL, f'The controlled crash fixture failed ({code}); see {log}'
        assert digest(jar, 'sha512') == original_hash
        receipt = dict(minecraft=mc, sha512=original_hash, status='fixture-staged', **fixture)
        (run / 'validation-crash-fixture.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt, indent=2))
        if runtime_guard: runtime_guard.close()
        return
    assert code == 0, f'Native smoke failed ({code}); see {log}'
    assert digest(jar, 'sha512') == original_hash, 'Artifact changed during runtime validation'
    text = log.read_text()
    assert 'VOLT_RUNTIME fullscreen=true' in text, 'Fullscreen state was not confirmed'
    if args.mode == 'library':
        assert 'VOLT_LIBRARY_SAFETY fullscreen trash/restore and feedback UI passed' in text
    elif args.mode == 'session':
        assert 'VOLT_SESSION_SAFETY saved after disconnect' in text
    else:
        assert (run / 'smoke-result.txt').read_text().startswith('Recorder=BUFFERING')
    new = set((run / 'volt/clips').glob('*.mp4')) - before
    receipt = dict(minecraft=mc, loader='fabric', fabric_loader=loader, version=meta['version'], sha512=original_hash,
        status='runtime-verified', mode=args.mode, failures=0, new_clips=len(new), log=str(log.relative_to(ROOT)))
    receipt.update(java_major=target['java'], fullscreen=True,
        installed_mod_jars=sorted(file.name for file in mods.glob('*.jar')))
    if args.mode != 'library':
        assert new, 'No new MP4 was created'
        clip = max(new, key=lambda file: file.stat().st_mtime)
        probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(clip)]))
        receipt['recording_seconds'] = float(probe['format']['duration']); assert receipt['recording_seconds'] > 1
        video = next(stream for stream in probe['streams'] if stream['codec_type'] == 'video')
        receipt.update(width=video['width'], height=video['height'])
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(clip), '-frames:v', '1', '-threads', '1', str(run / 'native-decoded.png')], check=True)
    (run / ('validation-' + args.mode + '.json')).write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))
    if runtime_guard:
        runtime_guard.close()


if __name__ == '__main__':
    main()
