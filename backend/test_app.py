import os
import tempfile

os.environ["CALLGUARD_DB"] = os.path.join(tempfile.mkdtemp(), "test.db")
os.environ["CALLGUARD_ADMIN_KEY"] = "secret-admin"

from fastapi.testclient import TestClient  # noqa: E402
import app as appmod  # noqa: E402

c = TestClient(appmod.app)
SPAMMER = "021 555 0199"


def dev(i):
    return {"X-Device-Id": f"device-{i:04d}"}


def test_lookup_unknown_and_normalised():
    r = c.get("/v1/lookup", params={"number": "082 123 4567"})
    assert r.status_code == 200
    j = r.json()
    assert j["number"] == "+27821234567"
    assert j["spam"]["verdict"] == "unknown"
    assert j["business"] is None
    assert j["line_type"] == "mobile"


def test_bad_number_rejected():
    assert c.get("/v1/lookup", params={"number": "hello"}).status_code == 400


def test_reports_build_spam_verdict_one_vote_per_device():
    for i in range(3):
        r = c.post("/v1/report", headers=dev(i),
                   json={"number": SPAMMER, "category": "scam", "label": "Fake bank"})
        assert r.status_code == 200
    # same device voting again does not inflate the count
    c.post("/v1/report", headers=dev(0), json={"number": SPAMMER, "category": "scam"})
    s = c.get("/v1/lookup", params={"number": SPAMMER}).json()["spam"]
    assert s["reports"] == 3
    assert s["verdict"] == "spam"
    assert s["top_category"] == "scam"
    assert "Fake bank" in s["labels"]
    top = c.get("/v1/top-spam").json()["numbers"]
    assert any(t["number"] == "+27215550199" for t in top)


def test_not_spam_votes_pull_score_down():
    n = "011 555 0100"
    c.post("/v1/report", headers=dev(10), json={"number": n, "category": "telemarketing"})
    for i in range(11, 16):
        c.post("/v1/report", headers=dev(i), json={"number": n, "is_spam": False})
    s = c.get("/v1/lookup", params={"number": n}).json()["spam"]
    assert s["verdict"] == "unknown"


def test_report_needs_device_and_valid_category():
    assert c.post("/v1/report", json={"number": SPAMMER}).status_code == 400
    assert c.post("/v1/report", headers=dev(1),
                  json={"number": SPAMMER, "category": "nope"}).status_code == 400


def test_business_hidden_until_verified():
    c.post("/v1/business", json={"number": "021 444 0000", "name": "Parow Pharmacy"})
    assert c.get("/v1/lookup", params={"number": "0214440000"}).json()["business"] is None
    assert c.post("/v1/admin/businesses/0214440000/verify").status_code == 403
    r = c.post("/v1/admin/businesses/0214440000/verify",
               headers={"X-Admin-Key": "secret-admin"})
    assert r.status_code == 200
    b = c.get("/v1/lookup", params={"number": "0214440000"}).json()["business"]
    assert b["name"] == "Parow Pharmacy"


def test_rate_limit():
    for i in range(appmod.MAX_REPORTS_PER_HOUR):
        c.post("/v1/report", headers=dev(999), json={"number": f"0215551{i:03d}"})
    r = c.post("/v1/report", headers=dev(999), json={"number": "0215559999"})
    assert r.status_code == 429
