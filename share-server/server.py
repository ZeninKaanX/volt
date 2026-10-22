"""Volt alpha share service. Put behind HTTPS for use outside localhost."""
from __future__ import annotations
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

ID = re.compile(r"^[A-Za-z0-9_-]{24}$")


class Store:
    def __init__(self, root: Path, base: str, tokens: list[str], max_bytes=250 * 1024**2, quota=10 * 1024**3, days=14):
        parsed = urlsplit(base)
        if parsed.scheme not in ("http", "https") or not parsed.netloc or parsed.path.rstrip("/") or parsed.query or parsed.fragment:
            raise ValueError("VOLT_PUBLIC_URL must be an HTTP(S) origin")
        if not tokens or any(len(t) < 24 for t in tokens):
            raise ValueError("Set a random VOLT_UPLOAD_TOKEN of at least 24 characters")
        self.root = root.resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.base = base.rstrip("/")
        self.tokens = tokens
        self.max_bytes, self.quota, self.lifetime = max_bytes, quota, days * 86400
        self.lock = threading.Lock()
        self.reserved = 0
        self.active = 0
        self.rates: dict[str, list[float]] = {}
        with self.db() as db:
            db.execute("CREATE TABLE IF NOT EXISTS clips (id TEXT PRIMARY KEY, owner TEXT NOT NULL, size INTEGER NOT NULL, expires INTEGER NOT NULL)")
        self.cleanup()

    def db(self):
        return sqlite3.connect(self.root / "index.sqlite", timeout=10)

    def owner(self, authorization):
        if not authorization.startswith("Bearer "):
            return None
        candidate = authorization[7:]
        for token in self.tokens:
            if hmac.compare_digest(candidate.encode(), token.encode()):
                return hashlib.sha256(token.encode()).hexdigest()
        return None

    def cleanup(self):
        with self.lock, self.db() as db:
            rows = db.execute("SELECT id FROM clips WHERE expires <= ?", (int(time.time()),)).fetchall()
            for (clip_id,) in rows:
                (self.root / (clip_id + ".mp4")).unlink(missing_ok=True)
            db.execute("DELETE FROM clips WHERE expires <= ?", (int(time.time()),))
            # Leftover partial uploads from a previous server crash.
            for part in self.root.glob("*.partial"):
                if part.stat().st_mtime < time.time() - 3600:
                    part.unlink(missing_ok=True)

    def lookup(self, clip_id):
        if not ID.fullmatch(clip_id):
            return None
        with self.db() as db:
            row = db.execute("SELECT size, expires, owner FROM clips WHERE id=? AND expires>?", (clip_id, int(time.time()))).fetchone()
        if row and (self.root / (clip_id + ".mp4")).is_file():
            return row
        return None


class Server(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, store):
        self.store = store
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.connection.settimeout(30)

    def log_message(self, fmt, *args):
        # No clip URLs, authorization headers or token values in access logs.
        pass

    def send(self, code, data=b"", kind="application/json", headers=None, head=False):
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Cache-Control", "no-store")
        if headers:
            for k, v in headers.items():
                self.send_header(k, v)
        self.end_headers()
        if not head:
            self.wfile.write(data)

    def error(self, code, message):
        self.close_connection = True
        self.send(code, json.dumps({"error": message}).encode(), headers={"Connection": "close"})

    def do_PUT(self):
        if self.path != "/api/clips":
            return self.error(404, "Not found")
        store = self.server.store
        owner = store.owner(self.headers.get("Authorization", ""))
        if owner is None:
            return self.error(401, "Invalid upload token")
        if self.headers.get("Transfer-Encoding"):
            return self.error(400, "Use Content-Length")
        try:
            size = int(self.headers["Content-Length"])
        except (TypeError, ValueError, KeyError):
            return self.error(411, "Content-Length required")
        if size < 32 or size > store.max_bytes:
            return self.error(413, "Clip too large or empty")
        if self.headers.get_content_type() != "video/mp4":
            return self.error(415, "MP4 required")
        store.cleanup()
        with store.lock, store.db() as db:
            used = db.execute("SELECT COALESCE(SUM(size),0) FROM clips").fetchone()[0]
            recent = [t for t in store.rates.get(owner, []) if t > time.time() - 60]
            if len(recent) >= 6 or store.active >= 2:
                return self.error(429, "Upload limit; try again later")
            if used + store.reserved + size > store.quota:
                return self.error(507, "Storage quota reached")
            store.rates[owner] = recent + [time.time()]
            store.reserved += size
            store.active += 1
        clip_id = secrets.token_urlsafe(18)
        partial = store.root / (clip_id + ".partial")
        target = store.root / (clip_id + ".mp4")
        try:
            prefix = self.rfile.read(32)
            if len(prefix) != 32 or prefix[4:8] != b"ftyp":
                return self.error(415, "Invalid MP4 header")
            with partial.open("xb") as file:
                file.write(prefix)
                remaining = size - 32
                while remaining:
                    chunk = self.rfile.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise OSError("Upload interrupted")
                    file.write(chunk)
                    remaining -= len(chunk)
            expires = int(time.time()) + store.lifetime
            with store.lock, store.db() as db:
                partial.replace(target)
                db.execute("INSERT INTO clips VALUES(?,?,?,?)", (clip_id, owner, size, expires))
            body = {"id": clip_id, "url": store.base + "/c/" + clip_id, "expiresAt": expires}
            self.send(201, json.dumps(body).encode())
        except (OSError, sqlite3.Error):
            target.unlink(missing_ok=True)
            try:
                self.error(500, "Upload failed")
            except OSError:
                pass
        finally:
            partial.unlink(missing_ok=True)
            with store.lock:
                store.reserved -= size
                store.active -= 1

    def do_DELETE(self):
        clip_id = self.path.removeprefix("/api/clips/")
        if not self.path.startswith("/api/clips/") or not ID.fullmatch(clip_id):
            return self.error(404, "Not found")
        store = self.server.store
        owner = store.owner(self.headers.get("Authorization", ""))
        if owner is None:
            return self.error(401, "Invalid upload token")
        with store.lock, store.db() as db:
            row = db.execute("SELECT owner FROM clips WHERE id=?", (clip_id,)).fetchone()
            if row is None or not hmac.compare_digest(row[0], owner):
                return self.error(404, "Not found")
            (store.root / (clip_id + ".mp4")).unlink(missing_ok=True)
            db.execute("DELETE FROM clips WHERE id=?", (clip_id,))
        self.send(204)

    def do_HEAD(self):
        self.get(head=True)

    def do_GET(self):
        self.get()

    def get(self, head=False):
        path = urlsplit(self.path).path
        if path == "/health":
            return self.send(200, b'{"status":"ok"}', head=head)
        if path.startswith("/c/"):
            clip_id = path[3:]
            if not self.server.store.lookup(clip_id):
                return self.error(404, "Clip expired or not found")
            base = self.server.store.base
            page = f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Clipped with Volt</title><meta property="og:title" content="Clipped with Volt"><meta property="og:type" content="video.other">
<meta property="og:video" content="{base}/media/{clip_id}.mp4"><meta property="og:video:type" content="video/mp4">
<style>body{{margin:0;background:#0b0c0f;color:#eceef2;font:16px system-ui;display:grid;place-items:center;min-height:100vh}}main{{width:min(1000px,94vw)}}h1{{font-size:20px;letter-spacing:3px}}video{{width:100%;max-height:75vh;border-radius:14px;background:#000}}p{{color:#949baa;font-size:13px}}</style>
<main><h1>VOLT</h1><video controls playsinline preload="metadata" src="/media/{clip_id}.mp4"></video><p>Clipped with Volt · Minecraft</p></main></html>'''.encode()
            return self.send(200, page, "text/html; charset=utf-8", {"Content-Security-Policy": "default-src 'none'; media-src 'self'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'"}, head)
        if path.startswith("/media/") and path.endswith(".mp4"):
            return self.media(path[7:-4], head)
        self.error(404, "Not found")

    def media(self, clip_id, head):
        store = self.server.store
        row = store.lookup(clip_id)
        if not row:
            return self.error(404, "Clip expired or not found")
        size = row[0]
        start, end = 0, size - 1
        range_value = self.headers.get("Range")
        if range_value:
            match = re.fullmatch(r"bytes=(\d*)-(\d*)", range_value)
            if not match or not (match[1] or match[2]):
                return self.error(416, "Invalid range")
            if match[1]:
                start = int(match[1]); end = min(size - 1, int(match[2])) if match[2] else size - 1
            else:
                start = max(0, size - int(match[2]))
            if start >= size or start > end:
                return self.error(416, "Range not satisfiable")
        try:
            file = (store.root / (clip_id + ".mp4")).open("rb")
        except FileNotFoundError:
            return self.error(404, "Not found")
        with file:
            self.send_response(206 if range_value else 200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Content-Length", str(end - start + 1))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Cache-Control", "private, max-age=60")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            if range_value:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            if not head:
                file.seek(start)
                remaining = end - start + 1
                while remaining:
                    chunk = file.read(min(1024 * 1024, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk); remaining -= len(chunk)


def main():
    store = Store(Path(os.environ.get("VOLT_STORAGE", "./data")), os.environ.get("VOLT_PUBLIC_URL", "http://127.0.0.1:8765"),
                  os.environ.get("VOLT_UPLOAD_TOKEN", "").split(","))
    server = Server((os.environ.get("VOLT_BIND", "127.0.0.1"), int(os.environ.get("VOLT_PORT", "8765"))), store)
    def cleanup():
        while True:
            time.sleep(3600)
            store.cleanup()
    threading.Thread(target=cleanup, daemon=True).start()
    print("Volt share server ready. External sharing requires an HTTPS reverse proxy.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
