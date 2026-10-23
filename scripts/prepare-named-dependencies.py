#!/usr/bin/env python3
"""Prepare verified official named client and published Fabric API compile inputs."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import zipfile
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('native_qa', ROOT / 'scripts/native-smoke.py')
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)
qa.CACHE = Path.home() / '.cache/volt-build/dependencies'
mc = sys.argv[1]
target = json.loads((ROOT / 'minecraft-targets.json').read_text())[mc]
assert target['adapter'] == 'named'
cache = Path(os.environ['VOLT_DEPENDENCY_CACHE']) if os.environ.get('VOLT_DEPENDENCY_CACHE') else None
manifest = qa.metadata('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', 'manifest.json')
entry = next(v for v in manifest['versions'] if v['id'] == mc)
game = qa.metadata(entry['url'], mc + '.json')
client = game['downloads']['client']
cached_client = cache / (mc + '-official.jar') if cache else None
client_file = cached_client if cached_client and cached_client.is_file() and qa.digest(cached_client) == client['sha1'] else qa.fetch(client['url'], qa.CACHE / (mc + '-client.jar'), client['sha1'])
jars = [client_file]
artifacts = [v['downloads']['artifact'] for v in game['libraries'] if qa.allowed(v) and 'artifact' in v.get('downloads', {}) and not v['name'].startswith('org.ow2.asm:')]
with ThreadPoolExecutor(max_workers=8) as pool:
    jars += list(pool.map(lambda item: qa.artifact(item, cache), artifacts))
profile = qa.metadata(f'https://meta.fabricmc.net/v2/versions/loader/{mc}/{target["loader_minimum"]}/profile/json', mc + '-fabric.json')
for item in profile['libraries']:
    group, name, version = item['name'].split(':')
    path = group.replace('.', '/') + f'/{name}/{version}/{name}-{version}.jar'
    jars.append(qa.artifact({'path': path, 'url': item.get('url', 'https://maven.fabricmc.net/') + path}, cache))
api = target['fabric_api']
path = f'net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar'
api_file = qa.artifact({'path': path, 'url': 'https://maven.fabricmc.net/' + path}, cache)
output = ROOT / 'build/named-dependencies' / mc
output.mkdir(parents=True, exist_ok=True)
for old in output.glob('*.jar'):
    old.unlink()
for index, file in enumerate(jars):
    (output / f'dependency-{index:03d}-{file.name}').symlink_to(file.resolve())
with zipfile.ZipFile(api_file) as archive:
    for name in archive.namelist():
        if name.startswith('META-INF/jars/') and name.endswith('.jar'):
            (output / Path(name).name).write_bytes(archive.read(name))
print(f'Prepared {len(list(output.glob("*.jar")))} named compile dependencies for {mc}')
