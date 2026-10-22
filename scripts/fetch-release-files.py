#!/usr/bin/env python3
"""Fetch the exact runtime-tested release files and verify their SHA-512 digests."""
import hashlib
import json
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
output = root / 'build/release-downloads'
output.mkdir(parents=True, exist_ok=True)
entries = json.loads((root / 'docs/publication/release-files.json').read_text())
checksums = []
for entry in entries:
    assert urllib.parse.urlsplit(entry['url']).hostname == 'cdn.modrinth.com'
    assert Path(entry['filename']).name == entry['filename'] and entry['filename'].endswith('.jar')
    target = output / entry['filename']
    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'Volt-release/0.6.0'})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    assert hashlib.sha512(data).hexdigest() == entry['sha512'], 'Release hash mismatch'
    target.write_bytes(data)
    with zipfile.ZipFile(target) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        assert metadata['id'] == 'volt' and metadata['depends']['minecraft'] == '~' + entry['minecraft']
        assert metadata['version'] == entry['version'].split('+')[0]
    checksums.append(entry['sha512'] + '  ' + entry['filename'])
    print('Verified', target.name)
(output / 'SHA512SUMS').write_text('\n'.join(checksums) + '\n')
