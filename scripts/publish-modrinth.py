#!/usr/bin/env python3
"""Publish a verified Volt build. Credentials are read from a private local file."""
import argparse
import hashlib
import json
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--token-file', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--slug', default='volt-clips')
    parser.add_argument('--source-url')
    parser.add_argument('--validation-file', type=Path)
    parser.add_argument('--submit', action='store_true')
    args = parser.parse_args()
    token = args.token_file.read_text().strip()

    def request(method, path, data=None, kind='application/json'):
        req = urllib.request.Request('https://api.modrinth.com/v2/' + path,
            data=data, method=method, headers={'Authorization': token,
            'User-Agent': 'Volt-publisher/0.7.0', 'Content-Type': kind})
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                body = response.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'Modrinth HTTP {error.code}: ' + error.read().decode()[:1000]) from None

    def multipart(path, metadata, file=None):
        boundary = uuid.uuid4().hex
        payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
                   'Content-Type: application/json\r\n\r\n' + json.dumps(metadata) + '\r\n').encode()
        if file:
            payload += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                        f'filename="{file.name}"\r\nContent-Type: application/java-archive\r\n\r\n').encode()
            payload += file.read_bytes() + b'\r\n'
        payload += f'--{boundary}--\r\n'.encode()
        return request('POST', path, payload, 'multipart/form-data; boundary=' + boundary)

    # Only metadata from this exact artifact may be advertised.
    jar = args.jar.resolve()
    with zipfile.ZipFile(jar) as archive:
        meta = json.loads(archive.read('fabric.mod.json'))
        assert meta['id'] == 'volt'
        assert 'dev/volt/VoltClient.class' in archive.namelist(), 'Missing client implementation'
        assert not any('voltsmoke' in name for name in archive.namelist())
        minecraft = meta['depends']['minecraft'].removeprefix('~')
        assert archive.read('assets/volt/font/ofl.txt')
    digest = hashlib.sha512(jar.read_bytes()).hexdigest()
    assert args.validation_file, 'Every build requires its own runtime validation receipt'
    receipt = json.loads(args.validation_file.read_text())
    assert receipt['minecraft'] == minecraft and receipt['loader'] == 'fabric'
    assert receipt['sha512'] == digest and receipt['status'] == 'runtime-verified'
    assert receipt['failures'] == 0 and receipt['recording_seconds'] > 0
    changelog_file = ROOT / 'docs/publication/changelogs' / (meta['version'] + '.md')
    assert changelog_file.is_file(), 'Missing changelog for this exact release'
    changelog = changelog_file.read_text()
    projects = request('GET', 'user/eververity/projects')
    project = next((p for p in projects if p['slug'] == args.slug), None)
    body = (ROOT / 'docs/publication/modrinth-body.md').read_text()
    fields = dict(title='Volt', description='Instant replay, automatic highlights and a local clip studio inside Minecraft.',
        body=body, categories=['utility', 'social'], additional_categories=[],
        client_side='required', server_side='unsupported', license_id='LicenseRef-All-Rights-Reserved',
        project_type='mod', slug=args.slug, initial_versions=[], is_draft=True)
    if args.source_url:
        fields.update(source_url=args.source_url, issues_url=args.source_url + '/issues')
    if project is None:
        project = multipart('project', fields)
    project_id = project['id']
    request('PATCH', 'project/' + project_id, json.dumps({key: fields[key] for key in
        ('title', 'description', 'body', 'categories', 'client_side', 'server_side')}).encode())
    if args.source_url:
        request('PATCH', 'project/' + project_id, json.dumps({key: fields[key] for key in
            ('source_url', 'issues_url')}).encode())
    icon = ROOT / 'src/main/resources/assets/volt/icon.png'
    request('PATCH', f'project/{project_id}/icon?ext=png', icon.read_bytes(), 'image/png')
    versions = request('GET', f'project/{project_id}/version')
    number = meta['version'] + '+mc' + minecraft + '.fabric'
    existing = next((v for v in versions if v['version_number'] == number), None)
    if existing:
        assert any(f['hashes']['sha512'] == digest for f in existing['files']), 'Published artifact differs'
        version = existing
    else:
        version = multipart('version', dict(project_id=project_id, name='Volt ' + meta['version'] + ' · ' + minecraft + ' Fabric',
            version_number=number, changelog=changelog,
            dependencies=[dict(project_id='P7dR8mSH', dependency_type='required')],
            game_versions=[minecraft], loaders=['fabric'], version_type='alpha', featured=True,
            file_parts=['file'], primary_file='file', status='listed'), jar)
    verified = request('GET', 'version/' + version['id'])
    assert verified['game_versions'] == [minecraft] and verified['loaders'] == ['fabric']
    assert verified['files'][0]['hashes']['sha512'] == digest
    screenshots = [('Clip library', 'studio-library.png'), ('Clip editor', 'studio-player.png'),
                   ('Export controls', 'studio-export.png'), ('Performance settings', 'studio-performance.png')]
    current = request('GET', 'project/' + project_id)
    for order, (title, filename) in enumerate(screenshots):
        if any(item.get('title') == title for item in current['gallery']):
            continue
        file = ROOT / 'docs/verification/performance-v6' / filename
        query = urllib.parse.urlencode(dict(ext='png', featured=str(order == 0).lower(), title=title,
            description='Volt 0.6.0 alpha, Minecraft 1.21.11 Fabric.', ordering=order))
        request('POST', f'project/{project_id}/gallery?' + query, file.read_bytes(), 'image/png')
    if args.submit:
        request('PATCH', 'project/' + project_id, json.dumps({'status': 'processing', 'requested_status': 'approved'}).encode())
    current = request('GET', 'project/' + project_id)
    receipt = dict(project_id=project_id, slug=current['slug'], status=current['status'],
        url='https://modrinth.com/mod/' + current['slug'], version_id=verified['id'],
        sha512=digest, minecraft=minecraft, loader='fabric')
    (ROOT / 'docs/publication/modrinth-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
