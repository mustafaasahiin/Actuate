# Actuate Server

Voice-to-action backend for the Actuate Android app.

## What it does

- **Parse** — `POST /api/v1/actions/parse` runs OpenRouter function-calling over a
  transcript and returns structured actions (calendar_event / list_item /
  reminder). The API key lives **server-side only**.
- **Execute** — `POST /api/v1/actions/execute` pushes each action to its
  destination:
  - calendar_event → **Google Calendar API** (service account or OAuth)
  - list_item → **Notion API**
  - reminder → persisted server-side and flagged `scheduleLocal` so the
    Android app schedules it via AlarmManager (EventKit's Android equivalent)
- **Quota** — free tier is 10 voice actions per week (rolling 7-day window,
  configurable via `FREE_QUOTA_PER_WEEK`), enforced server-side. Pro users
  bypass it.
- **Auth** — lightweight bearer-token registration (`/api/v1/auth/register`),
  session re-hydration (`/api/v1/auth/me`).
- **Subscriptions** — `POST /api/v1/webhooks/revenuecat` flips a user to Pro
  when RevenueCat reports an active entitlement.

## Run it (development)

```bash
cd server
npm install
copy .env.example .env   # then fill in keys
npm run dev              # http://localhost:8787
```

Health check: `GET http://localhost:8787/health`

The Android emulator reaches this via `http://10.0.2.2:8787`. A physical
device uses your machine's LAN IP.

## Configuration (`.env`)

| Key | Purpose |
|-----|---------|
| `OPENROUTER_API_KEY` | OpenRouter key for action parsing (https://openrouter.ai/keys, never shipped in the app) |
| `NOTION_INTEGRATION_TOKEN` | Notion token for list items |
| `NOTION_DATABASES` | JSON map `{"shopping":"<db_id>","general":"<db_id>"}` |
| `GOOGLE_SERVICE_ACCOUNT_EMAIL` + `GOOGLE_SERVICE_ACCOUNT_KEY(_FILE)` | Service account for Google Calendar (share the calendar with it) |
| `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`/`GOOGLE_REFRESH_TOKEN` | Alternative OAuth flow |
| `GOOGLE_CALENDAR_ID` | Calendar to write into (default `primary`) |
| `PRO_USER_EMAILS` | Comma-separated emails treated as Pro (dev mode) |
| `FREE_QUOTA_PER_WEEK` | Free-tier limit, rolling 7-day window (default 10) |

Without keys the server still runs: `/health` reports which integrations are
live, and the app falls back to its on-device parsers/executors.

### Rate limiting

Applied to every request (per IP):

| Scope | Limit |
|-------|-------|
| Global (all routes) | 300/min |
| `POST /api/v1/auth/register` | 5/hour |
| `POST /api/v1/actions/parse` | 30/hour |
| `POST /api/v1/actions/execute` | 120/hour (plus the per-user weekly quota) |
| `POST /api/v1/webhooks` | 60/min |

Responses are JSON `{ error, code: "rate_limited" }` with HTTP 429.

### Sessions

- `POST /register` upserts by `deviceId` — the app auto-registers on every
  launch, and the same device always maps to the same user row.
- Tokens are 256-bit random, valid 30 days, pruned on read.
- Legacy plain-string tokens from older `db.json` files are migrated on read.

## API

| Method | Path | Auth | Body |
|--------|------|------|------|
| GET | `/health` | – | – |
| POST | `/api/v1/auth/register` | – | `{ name?, email?, deviceId }` |
| GET | `/api/v1/auth/me` | Bearer | – |
| POST | `/api/v1/actions/parse` | Bearer | `{ transcript }` |
| POST | `/api/v1/actions/execute` | Bearer | `{ actions: [{ id, type, ... }] }` |
| GET | `/api/v1/actions` | Bearer | – (full history, newest first) |
| GET | `/api/v1/actions/lists` | Bearer | – (list items grouped by list) |
| POST | `/api/v1/actions/:id/done` | Bearer | `{ done: boolean }` (also syncs the Notion "Done" checkbox best-effort) |
| POST | `/api/v1/webhooks/revenuecat` | – | RevenueCat event payload |

State persists to `server/data/db.json` (dev grade; swap for Postgres/Redis
before production).