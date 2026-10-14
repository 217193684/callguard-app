"""
CallGuard backend — the shared spam database behind the Android app.

What it stores:
  * Spam / not-spam votes, one vote per device per number (latest vote wins)
  * Business names, which only appear in lookups after an admin verifies them
    (so nobody can attach a name to a private person's number)

What it never stores: users' contact lists, or names for private numbers.

Run:  uvicorn app:app --host 0.0.0.0 --port 8000
"""
import os
import sqlite3
import threading
import time
from collections import defaultdict, deque
from contextlib import contextmanager
from typing import Optional

import phonenumbers
from phonenumbers import carrier, geocoder, PhoneNumberType
from fastapi import FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field

DB_PATH = os.environ.get("CALLGUARD_DB", "callguard.db")
DEFAULT_REGION = os.environ.get("CALLGUARD_REGION", "ZA")
ADMIN_KEY = os.environ.get("CALLGUARD_ADMIN_KEY", "")   # set this before going live
REPORT_WINDOW_DAYS = 180          # votes older than this stop counting
MAX_REPORTS_PER_HOUR = 30         # per device

CATEGORIES = {
    "scam", "telemarketing", "robocall", "debt_collector",
    "survey", "harassment", "other",
}

app = FastAPI(title="CallGuard API", version="1.0")
_lock = threading.Lock()
_rate: dict[str, deque] = defaultdict(deque)


# ---------------------------------------------------------------- database
SCHEMA = """
CREATE TABLE IF NOT EXISTS reports (
    number      TEXT NOT NULL,
    device      TEXT NOT NULL,
    is_spam     INTEGER NOT NULL,
    category    TEXT NOT NULL,
    label       TEXT,
    created_at  INTEGER NOT NULL,
    PRIMARY KEY (number, device)
);
CREATE INDEX IF NOT EXISTS idx_reports_number ON reports(number);
CREATE TABLE IF NOT EXISTS businesses (
    number      TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    category    TEXT NOT NULL,
    verified    INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
);
"""


@contextmanager
def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db():
    with db() as conn:
        conn.executescript(SCHEMA)


init_db()


# ---------------------------------------------------------------- helpers
def normalize(raw: str) -> phonenumbers.PhoneNumber:
    try:
        p = phonenumbers.parse(raw, DEFAULT_REGION)
    except phonenumbers.NumberParseException:
        raise HTTPException(400, "Not a phone number")
    if not phonenumbers.is_possible_number(p):
        raise HTTPException(400, "Not a possible phone number")
    return p


def e164(p) -> str:
    return phonenumbers.format_number(p, phonenumbers.PhoneNumberFormat.E164)


LINE_TYPES = {
    PhoneNumberType.MOBILE: "mobile",
    PhoneNumberType.FIXED_LINE: "landline",
    PhoneNumberType.FIXED_LINE_OR_MOBILE: "landline_or_mobile",
    PhoneNumberType.TOLL_FREE: "toll_free",
    PhoneNumberType.PREMIUM_RATE: "premium_rate",
    PhoneNumberType.SHARED_COST: "shared_cost",
    PhoneNumberType.VOIP: "voip",
    PhoneNumberType.UAN: "uan",
}


def spam_summary(conn, number: str) -> dict:
    since = int(time.time()) - REPORT_WINDOW_DAYS * 86400
    rows = conn.execute(
        "SELECT is_spam, category, label FROM reports WHERE number=? AND created_at>=?",
        (number, since),
    ).fetchall()
    spam = [r for r in rows if r["is_spam"]]
    ham = len(rows) - len(spam)
    score = round(100 * len(spam) / (len(spam) + ham + 2)) if rows else 0
    cats: dict[str, int] = defaultdict(int)
    for r in spam:
        cats[r["category"]] += 1
    labels: dict[str, int] = defaultdict(int)
    for r in spam:
        if r["label"]:
            labels[r["label"]] += 1
    return {
        "score": score,
        "reports": len(spam),
        "not_spam": ham,
        "verdict": verdict(len(spam), score),
        "top_category": max(cats, key=cats.get) if cats else None,
        "labels": sorted(labels, key=labels.get, reverse=True)[:3],
    }


def verdict(spam_reports: int, score: int) -> str:
    if spam_reports >= 3 and score >= 60:
        return "spam"
    if spam_reports >= 1 and score >= 30:
        return "suspicious"
    return "unknown"


def rate_limit(device: str):
    now = time.time()
    with _lock:
        q = _rate[device]
        while q and now - q[0] > 3600:
            q.popleft()
        if len(q) >= MAX_REPORTS_PER_HOUR:
            raise HTTPException(429, "Too many reports, try later")
        q.append(now)


def require_device(device_id: Optional[str]) -> str:
    if not device_id or not (8 <= len(device_id) <= 64):
        raise HTTPException(400, "Missing X-Device-Id header")
    return device_id


# ---------------------------------------------------------------- models
class Report(BaseModel):
    number: str
    is_spam: bool = True
    category: str = "other"
    label: Optional[str] = Field(None, max_length=60)


class Business(BaseModel):
    number: str
    name: str = Field(..., min_length=2, max_length=80)
    category: str = Field("business", max_length=40)


# ---------------------------------------------------------------- routes
@app.get("/health")
def health():
    return {"ok": True}


@app.get("/v1/lookup")
def lookup(number: str = Query(...)):
    p = normalize(number)
    n = e164(p)
    with db() as conn:
        biz = conn.execute(
            "SELECT name, category FROM businesses WHERE number=? AND verified=1", (n,)
        ).fetchone()
        spam = spam_summary(conn, n)
    return {
        "number": n,
        "valid": phonenumbers.is_valid_number(p),
        "carrier": carrier.name_for_number(p, "en") or None,
        "location": geocoder.description_for_number(p, "en") or None,
        "line_type": LINE_TYPES.get(phonenumbers.number_type(p), "unknown"),
        "business": {"name": biz["name"], "category": biz["category"], "verified": True}
        if biz else None,
        "spam": spam,
    }


@app.post("/v1/report")
def report(body: Report, x_device_id: Optional[str] = Header(None)):
    device = require_device(x_device_id)
    if body.category not in CATEGORIES:
        raise HTTPException(400, f"category must be one of {sorted(CATEGORIES)}")
    n = e164(normalize(body.number))
    rate_limit(device)
    label = body.label.strip() if body.label and body.is_spam else None
    with db() as conn:
        conn.execute(
            """INSERT INTO reports(number, device, is_spam, category, label, created_at)
               VALUES (?,?,?,?,?,?)
               ON CONFLICT(number, device) DO UPDATE SET
                 is_spam=excluded.is_spam, category=excluded.category,
                 label=excluded.label, created_at=excluded.created_at""",
            (n, device, int(body.is_spam), body.category, label, int(time.time())),
        )
        spam = spam_summary(conn, n)
    return {"number": n, "spam": spam}


@app.get("/v1/top-spam")
def top_spam(limit: int = Query(500, ge=1, le=5000)):
    """Numbers the app downloads so it can block spam even when offline."""
    since = int(time.time()) - REPORT_WINDOW_DAYS * 86400
    with db() as conn:
        rows = conn.execute(
            """SELECT number, SUM(is_spam) AS spam, COUNT(*) - SUM(is_spam) AS ham
               FROM reports WHERE created_at >= ?
               GROUP BY number HAVING spam >= 1
               ORDER BY spam DESC LIMIT ?""",
            (since, limit),
        ).fetchall()
        out = []
        for r in rows:
            s = spam_summary(conn, r["number"])
            if s["verdict"] == "unknown":
                continue
            out.append({
                "number": r["number"], "score": s["score"], "reports": s["reports"],
                "verdict": s["verdict"], "top_category": s["top_category"],
                "label": s["labels"][0] if s["labels"] else None,
            })
    return {"numbers": out}


@app.post("/v1/business")
def register_business(body: Business):
    """A business registers its own number. Hidden until an admin verifies it."""
    n = e164(normalize(body.number))
    with db() as conn:
        conn.execute(
            """INSERT INTO businesses(number, name, category, verified, created_at)
               VALUES (?,?,?,0,?)
               ON CONFLICT(number) DO UPDATE SET name=excluded.name,
                 category=excluded.category, verified=0""",
            (n, body.name.strip(), body.category.strip(), int(time.time())),
        )
    return {"number": n, "status": "pending_verification"}


def require_admin(key: Optional[str]):
    if not ADMIN_KEY or key != ADMIN_KEY:
        raise HTTPException(403, "Admin key required")


@app.get("/v1/admin/businesses")
def pending_businesses(x_admin_key: Optional[str] = Header(None)):
    require_admin(x_admin_key)
    with db() as conn:
        rows = conn.execute(
            "SELECT number, name, category, verified FROM businesses ORDER BY created_at DESC"
        ).fetchall()
    return {"businesses": [dict(r) for r in rows]}


@app.post("/v1/admin/businesses/{number}/verify")
def verify_business(number: str, x_admin_key: Optional[str] = Header(None)):
    require_admin(x_admin_key)
    n = e164(normalize(number))
    with db() as conn:
        cur = conn.execute("UPDATE businesses SET verified=1 WHERE number=?", (n,))
    if cur.rowcount == 0:
        raise HTTPException(404, "No such business")
    return {"number": n, "verified": True}
