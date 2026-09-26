# Actuate Server

Express API that the Actuate Android client talks to when it needs cloud parsing or a
destination integration it cannot perform on device.

**Design rule: the app must remain fully usable when this server is unreachable.** Every
endpoint below has a documented offline behaviour, and none of them is on the critical
path for capturing a thought or scheduling a device reminder.

---

## Running

```bash
cd server
npm install
npm start          # default port 8787
npm test           # integration + LLM unit suites
```

Configuration is read from the environment (`src/config.js`). Nothing here requires an
LLM key to boot: without one, the parser degrades to the deterministic rule engine and
reports `simulated: true` in its responses.

---

## Base URL and versioning

```
https://<host>:8787/api/v1
```

All routes are JSON. Authentication is a bearer token issued at register/login, except
`/health`, `/auth/register` and `/auth/login`.

---

## Endpoints

### System

#### `GET /health`
Unauthenticated liveness probe. Used by the Android Settings screen's latency tester and
by the nginx upstream check.

```json
{ "status": "ok", "time": "2026-09-18T10:00:00.000Z" }
```

---

### Authentication

#### `POST /auth/register`
Creates a device-scoped account. Rate limited (registration limiter).

| Field | Type | Required | Notes |
|---|---|---|---|
| `email` | string | yes | |
| `password` | string | yes | |
| `name` | string | no | |
| `deviceId` | string | no | Stable per install; used for quota accounting |

```json
{ "token": "…", "user": { "id": "…", "email": "…" }, "quotaRemaining": 25 }
```

#### `POST /auth/login`
Issues a fresh token. Rate limited (login limiter) with a tighter budget than register.

#### `GET /auth/me`
Returns the caller's session, plan and remaining quota. This is what the app's
"Connected · N left this week" status line is derived from, and what the Settings latency
tester times.

```json
{
  "user": { "id": "…", "email": "…" },
  "plan": "free",
  "quotaRemaining": 18
}
```

---

### Actions

#### `POST /actions/parse`
Turns a transcript into structured intents **without executing anything**. The app calls
this to populate the staging deck.

Request:
```json
{ "transcript": "meeting with Priya at 3pm, buy eggs, remind me at 8pm" }
```

Response:
```json
{
  "source": "LLM",
  "confidence": 0.92,
  "actions": [
    { "type": "calendar_event", "title": "Meeting with Priya", "start": "2026-09-18T15:00:00Z" },
    { "type": "list_item", "text": "buy eggs", "list": "shopping" },
    { "type": "reminder", "title": "call the bank", "dueAt": "2026-09-18T20:00:00Z" }
  ]
}
```

#### `POST /actions/execute`
Executes already-parsed actions. This is the only endpoint that mutates a destination.

```json
{
  "actions": [ { "type": "calendar_event", "title": "…", "start": "…" } ]
}
```

Response is a per-action outcome list, so a partial failure is representable without
failing the whole request:

```json
{
  "executed": [
    { "actionId": "a1", "destination": "calendar", "success": true,  "message": "Added to Google Calendar" },
    { "actionId": "a2", "destination": "notion",   "success": false, "message": "Notion not connected" }
  ]
}
```

#### `GET /actions/lists`
Returns the user's Notion lists and their items, used to render the Lists tab.

#### `POST /actions/:id/done`
Marks a list item done/undone. The Android client applies the change optimistically to
DataStore first and reconciles with this endpoint when connectivity allows.

#### `GET /actions`
Returns recent action history. The client keeps its own local history, so this is a
reconciliation source rather than a dependency.

---

### Webhooks

#### `POST /webhooks/revenuecat`
Entitlement webhook. Signature-verified; drives the `pro` plan server-side so a client
that was reinstalled is still recognised as Pro.

---

## Rate limiting

| Tier | Applies to | Purpose |
|---|---|---|
| `registerLimiter` | `POST /auth/register` | Stops account farming from one device |
| `loginLimiter` | `POST /auth/login` | Credential-stuffing protection |
| Quota | `POST /actions/execute` | Free plan allowance, enforced per user |

Quota is **not** consumed by `/actions/parse`, and not consumed at all on the on-device
`LOCAL_RULE` path — a user with no network and no quota can still capture, parse and
schedule reminders locally.

---

## Live vs simulated behaviour

The server degrades instead of failing, and says which mode it used:

| Condition | Behaviour | Reported as |
|---|---|---|
| LLM key present, provider reachable | Real model parsing | `source: "LLM"` |
| No LLM key / provider error | Deterministic rule parser | `source: "RULES"` |
| Google Calendar connected | Live OAuth2 sync | `destination: "calendar"` |
| Calendar not connected | Action stored locally on device | `destination: "local"` |
| Notion connected | Live Notion write | `destination: "notion"` |
| Notion not connected | Action stored locally, queued for sync | `destination: "local"` |

The client never has to guess: the staging deck renders the mode badge from
`source`, and the Calendar screen renders `[Live Google Calendar]` vs `[Local cache]`
from the same signal.

---

## Tests

```bash
npm test
```

- `test/server.test.js` — endpoint integration coverage (auth, parse, execute, lists,
  done, webhooks), including auth failures and quota exhaustion
- `test/llm-phase1.test.js` — parser unit coverage, including malformed model output

---

## Deployment notes

- `actuate.nginx` contains the reverse-proxy config, including the TLS termination used
  by the client's pinned hostname.
- The server is stateless apart from its datastore; it can be restarted mid-demo without
  losing client state, because the client is offline-first by design.
