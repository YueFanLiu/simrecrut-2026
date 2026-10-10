"""Check transfer integrity and resume behaviour using a local HTTP server."""

import hashlib
import socket
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from simrecrut_ml.data.acquisition.download import (
    DownloadError,
    DownloadSpec,
    download_file,
)


class _SourceHandler(BaseHTTPRequestHandler):
    payload = b"synthetic-data-" * 4096
    requests = []
    interrupted = False
    ignore_range = False
    bad_range = False
    html_response = False

    def log_message(self, format, *args):
        pass

    def do_GET(self):
        handler = type(self)
        handler.requests.append({"path": self.path, "range": self.headers.get("Range")})
        byte_range = self.headers.get("Range")
        start = int(byte_range.split("=")[1].split("-")[0]) if byte_range else 0
        if handler.ignore_range:
            start = 0
        self.send_response(206 if start else 200)
        content_type = "text/html" if handler.html_response else "application/octet-stream"
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(handler.payload) - start))
        self.send_header("ETag", '"synthetic-source-v1"')
        if start:
            reported = start + 1 if handler.bad_range else start
            content_range = f"bytes {reported}-{len(handler.payload)-1}/{len(handler.payload)}"
            self.send_header("Content-Range", content_range)
        self.end_headers()
        if handler.interrupted and not start:
            self.wfile.write(handler.payload[:4096])
            self.wfile.flush()
            self.connection.shutdown(socket.SHUT_RDWR)
            self.connection.close()
            handler.interrupted = False
        else:
            self.wfile.write(handler.payload[start:])


class DownloadTests(unittest.TestCase):
    """Keep incomplete bytes local and bind cache reuse to publisher identity."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.destination = Path(self.temporary.name) / "source.bin"
        self.handler = type("TestHandler", (_SourceHandler,), {
            "requests": [], "interrupted": False, "ignore_range": False,
            "bad_range": False, "html_response": False,
        })
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), self.handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}/source"
        self.digest = hashlib.sha256(self.handler.payload).hexdigest()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temporary.cleanup()

    def test_checksum_and_atomic_completion(self):
        spec = DownloadSpec(self.url, len(self.handler.payload), self.digest)
        result = download_file(spec, self.destination)
        self.assertEqual(result["sha256"], self.digest)
        self.assertEqual(self.destination.read_bytes(), self.handler.payload)
        self.assertFalse(self.destination.with_name("source.bin.part").exists())

    def test_interrupted_transfer_resumes_confirmed_range(self):
        self.handler.interrupted = True
        spec = DownloadSpec(self.url, len(self.handler.payload), self.digest)
        with self.assertRaises(DownloadError):
            download_file(spec, self.destination, attempts=1)
        self.assertFalse(self.destination.exists())
        self.assertEqual(self.destination.with_name("source.bin.part").stat().st_size, 4096)
        download_file(spec, self.destination, attempts=1)
        self.assertEqual(self.handler.requests[-1]["range"], "bytes=4096-")
        self.assertEqual(self.destination.read_bytes(), self.handler.payload)

    def test_server_ignoring_range_restarts_instead_of_appending(self):
        self.handler.interrupted = True
        spec = DownloadSpec(self.url, len(self.handler.payload), self.digest)
        with self.assertRaises(DownloadError):
            download_file(spec, self.destination, attempts=1)
        self.handler.ignore_range = True
        download_file(spec, self.destination, attempts=1)
        self.assertEqual(self.destination.read_bytes(), self.handler.payload)

    def test_inconsistent_range_does_not_replace_destination(self):
        self.handler.interrupted = True
        spec = DownloadSpec(self.url, len(self.handler.payload), self.digest)
        with self.assertRaises(DownloadError):
            download_file(spec, self.destination, attempts=1)
        self.handler.bad_range = True
        with self.assertRaisesRegex(DownloadError, "inconsistent byte range"):
            download_file(spec, self.destination, attempts=1)
        self.assertFalse(self.destination.exists())

    def test_checksum_failure_preserves_existing_file(self):
        self.destination.write_bytes(b"existing synthetic source")
        spec = DownloadSpec(self.url, len(self.handler.payload), "0" * 64)
        with self.assertRaisesRegex(DownloadError, "checksum"):
            download_file(spec, self.destination)
        self.assertEqual(self.destination.read_bytes(), b"existing synthetic source")
        self.assertFalse(self.destination.with_name("source.bin.part").exists())

    def test_unhashed_cache_requires_identity_and_content_receipt(self):
        first = DownloadSpec(self.url)
        download_file(first, self.destination)
        download_file(first, self.destination)
        self.assertEqual(len(self.handler.requests), 1)
        download_file(DownloadSpec(self.url + "?version=2"), self.destination)
        self.assertEqual(len(self.handler.requests), 2)
        self.destination.write_bytes(b"changed locally")
        download_file(DownloadSpec(self.url + "?version=2"), self.destination)
        self.assertEqual(len(self.handler.requests), 3)
        self.assertEqual(self.destination.read_bytes(), self.handler.payload)

    def test_html_page_is_not_saved_as_dataset(self):
        self.handler.html_response = True
        with self.assertRaisesRegex(DownloadError, "page instead"):
            download_file(DownloadSpec(self.url), self.destination)
        self.assertFalse(self.destination.exists())
