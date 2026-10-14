# CallGuard — your own Truecaller-style caller ID and spam blocker

Two parts:

| Folder | What it is |
|---|---|
| `backend/` | Python server holding the shared spam database (FastAPI + SQLite) |
| `android/` | The Android app (Kotlin, Android 10 and newer) |

## What it does

- **Caller ID while the phone rings** — a notification shows *Likely spam*, a community label ("Fake bank call"), a verified business name, or the number with its original network and country.
- **Auto-blocks spam** — calls the community has reported (3+ reports, 60%+ spam votes) are rejected silently. Toggle in Settings.
- **Your own block list** — blocked numbers never ring, even offline.
- **One-tap reporting** — *Report spam* / *Not spam* buttons right on the caller notification. Reporting spam also blocks the number on your phone.
- **Number lookup** — search any number to see its spam score, votes, category and network.
- **Works offline** — the app downloads the top spam list twice a day.
- **Block hidden numbers** (optional).

### How it differs from Truecaller (on purpose)

Truecaller learns names by uploading every user's address book. CallGuard doesn't — that would mean collecting other people's personal information without their consent, which POPIA (and GDPR) don't allow. So:

- Names only come from **businesses that register their own number** and that you, as admin, verify.
- Spam info comes from **reports**, with one vote per phone and the latest vote winning.
- Your contacts never leave your phone. Android doesn't even ask CallGuard about calls from saved contacts.

---

## 1. Run the backend on your Windows PC

Needs Python 3.10+ ([python.org](https://www.python.org/downloads/), tick **Add to PATH**).

```powershell
cd callguard\backend
powershell -ExecutionPolicy Bypass -File .\run_server.ps1
```

The first run creates a virtual environment and installs packages. It then prints your PC's address, e.g. `http://192.168.1.23:8000`. Keep that window open.

- Check it's working: open `http://localhost:8000/docs` in your browser — you get a page where you can try every endpoint.
- If the phone can't connect, allow Python through Windows Firewall (Windows will usually pop up a prompt; tick **Private networks**).
- Edit the `CALLGUARD_ADMIN_KEY` line in `run_server.ps1` to a long random string.

Run the tests: `.\.venv\Scripts\pip install pytest httpx` then `.\.venv\Scripts\python -m pytest`.

## 2. Build and install the app

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** and choose the `callguard/android` folder. Let it sync (first time downloads Gradle and the Android libraries — several minutes). If it offers to upgrade the Android Gradle Plugin, accept.
3. On your phone: **Settings → About phone → tap Build number 7 times**, then **Developer options → USB debugging** on. Plug in the phone.
4. Press the green ▶ Run button. The app installs and opens.
5. In the app:
   - Tap **Enable call screening** and choose CallGuard.
   - Tap **Allow notifications**.
   - **Settings** tab → put in the address from step 1 → **Save & test** → should say *Connected ✔*.

To get an installable APK file to share: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## 3. Try it

From the PC's `/docs` page, `POST /v1/report` three times with different `X-Device-Id` headers (any 8+ characters) for a test number, e.g. a friend's phone with their permission. Then have them call you: it should be rejected and a quiet *Blocked* notification appears. Tap **Not spam** to undo.

Or in the app: **Lookup** tab → search a number → **Spam + block**.

## 4. Verify businesses (admin)

A business registers itself:

```powershell
curl.exe -X POST http://localhost:8000/v1/business -H "Content-Type: application/json" -d '{\"number\":\"021 444 0000\",\"name\":\"Parow Pharmacy\"}'
```

It stays hidden until you check it and verify:

```powershell
curl.exe http://localhost:8000/v1/admin/businesses -H "X-Admin-Key: YOUR_KEY"
curl.exe -X POST http://localhost:8000/v1/admin/businesses/0214440000/verify -H "X-Admin-Key: YOUR_KEY"
```

## 5. Going beyond your home Wi-Fi

At home, the phone reaches your PC directly. To make it work everywhere (and for other people), host the backend online:

- `backend/Dockerfile` is ready for any Docker host (Render, Railway, Fly.io, a small VPS). Mount a disk at `/data` so the database survives restarts, and set `CALLGUARD_ADMIN_KEY`.
- Use the `https://` address in the app's Settings. Once you're on https only, set `usesCleartextTraffic` to `false` in `AndroidManifest.xml`.
- If you open it to the public, you're running a service that processes phone numbers: publish a privacy notice and have a way for people to ask for their number's reports to be removed.

## API summary

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/lookup?number=` | Spam score, verdict, verified business, network, location |
| POST | `/v1/report` | `{number, is_spam, category, label}` + `X-Device-Id` header |
| GET | `/v1/top-spam?limit=` | List the app caches for offline blocking |
| POST | `/v1/business` | Business registers its own number (pending) |
| GET/POST | `/v1/admin/businesses[/{n}/verify]` | Admin review, needs `X-Admin-Key` |

Categories: `scam, telemarketing, robocall, debt_collector, survey, harassment, other`.

## Limits worth knowing

- Android only lets CallGuard screen calls from numbers **not in your contacts**.
- The network shown (Vodacom, MTN…) is where the number was first issued; ported numbers may now be on another network.
- Showing a pop-up *over* the call screen like Truecaller needs the "display over other apps" permission and a custom overlay; CallGuard uses a heads-up notification instead, which is simpler and works on all phones.
- Spam detection is only as good as the number of people reporting. With just you, it's mostly your personal block list plus whatever you report.
