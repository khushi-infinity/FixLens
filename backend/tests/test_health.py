"""Backend tests: /health contract.

The Phase 1-era test asserting /v1/diagnose returns 501 was retired in Phase 2:
real diagnosis lives at /api/v1/diagnose (see test_diagnose_endpoint.py), and
the old unversioned path no longer exists.
"""
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_returns_ok_and_version():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert isinstance(body["version"], str) and body["version"]


def test_old_unversioned_diagnose_path_is_gone():
    response = client.post(
        "/v1/diagnose",
        files={"image": ("test.jpg", b"not-a-real-image", "image/jpeg")},
    )
    assert response.status_code == 404
