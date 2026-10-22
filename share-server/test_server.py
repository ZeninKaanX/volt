import http.client
import json
from pathlib import Path
import tempfile
import threading
import unittest
from server import Store, Server

TOKEN = 'test-token-only-abcdefghijklmnopqrstuvwxyz'
VIDEO = b'\x00\x00\x00\x20ftypisom' + b'\x00' * 1024

class ShareServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.temp.name), 'https://clips.example.test', [TOKEN], max_bytes=2048, quota=4096)
        self.server = Server(('127.0.0.1', 0), self.store)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.temp.cleanup()
    def request(self, method, path, data=None, headers=None):
        conn = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        conn.request(method, path, data, headers or {})
        result = conn.getresponse(); code, hdrs, body = result.status, dict(result.getheaders()), result.read()
        conn.close()
        return code, hdrs, body
    def upload(self, token=TOKEN, video=VIDEO):
        return self.request('PUT', '/api/clips', video, {'Content-Type':'video/mp4','Authorization':'Bearer '+token})
    def test_upload_range_link_and_owner_delete(self):
        code, _, body = self.upload(); self.assertEqual(code, 201)
        data = json.loads(body); clip_id = data['id']
        self.assertEqual(data['url'], 'https://clips.example.test/c/' + clip_id)
        self.assertEqual(self.request('GET', '/c/'+clip_id)[0], 200)
        code, headers, body = self.request('GET', '/media/'+clip_id+'.mp4', headers={'Range':'bytes=4-7'})
        self.assertEqual(code, 206); self.assertEqual(body, b'ftyp'); self.assertEqual(headers['Content-Range'], f'bytes 4-7/{len(VIDEO)}')
        self.assertEqual(self.request('DELETE', '/api/clips/'+clip_id)[0], 401)
        self.assertEqual(self.request('DELETE', '/api/clips/'+clip_id, headers={'Authorization':'Bearer '+TOKEN})[0], 204)
        self.assertEqual(self.request('GET', '/c/'+clip_id)[0], 404)
    def test_rejects_bad_token_invalid_mp4_and_oversize(self):
        self.assertEqual(self.upload('wrong')[0], 401)
        self.assertEqual(self.upload(video=b'bad'*20)[0], 415)
        self.assertEqual(self.upload(video=VIDEO*3)[0], 413)
    def test_expiry_and_path_traversal(self):
        clip_id = json.loads(self.upload()[2])['id']
        with self.store.db() as db:
            db.execute('UPDATE clips SET expires=0 WHERE id=?', (clip_id,))
        self.assertEqual(self.request('GET', '/media/'+clip_id+'.mp4')[0], 404)
        self.store.cleanup()
        self.assertFalse((self.store.root/(clip_id+'.mp4')).exists())
        self.assertEqual(self.request('GET', '/media/../index.sqlite.mp4')[0], 404)

if __name__ == '__main__':
    unittest.main()
