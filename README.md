# Actuate

**Speak. Actuate.** — a native Android voice assistant that turns spoken commands
into real actions: calendar events, Notion list items, and device reminders.

Built for ShipAThon: voice in, actions out — with an Apple-inspired design
language, an LLM backend, and a free-tier quota with a paid Pro upgrade path.

---

## The demo (60 seconds)

1. Open Actuate. Grant microphone + notifications when asked.
2. Tap the blue voice bubble and say:
   - *"Add a meeting with Priya tomorrow at 3 PM"*
   - *"Put milk and eggs on the shopping list"*
   - *"Remind me to stretch at 5 PM"*
3. Watch the results land in **Today**, **Lists**, and **History** — and
   check your Google Calendar / Notion page if integrations are configured.
4. The bubble glows while listening, pulses while parsing, and shows a ring
   while executing.

## What makes it award-worthy

- **Voice → structured actions end-to-end.** Speech recognition on-device,
  OpenRouter function-calling (with a hardened system prompt that treats the
  transcript as untrusted data), then real execution against Google Calendar
  and Notion APIs. Reminders are scheduled locally via AlarmManager.
- **Works offline-first.** If the server or LLM is down, a rule-based parser
  and local execution still handle reminders and lists — the demo never dies.
- **A real business model.** Server-enforced 7-day rolling quota (free tier),
  RevenueCat webhook upgrades to Pro, Keystore-encrypted tokens.
- **A disciplined design system.** `design.md` defines an Apple-inspired token
  set (one blue, one job; hairline borders, never shadows; 8dp containers,
  980dp capsules; SF Pro-inspired Inter type scale) and every screen follows it.

## Architecture

```
app      Compose UI (Home, Today, Lists, History, Settings) + session bootstrap
core     Design system: tokens, type, shapes, shared components
domain   Pure-Kotlin business logic: models, parsers, quota policy, use cases
data     Retrofit-free OkHttp API client, DataStore, Android Keystore secrets,
         AlarmManager reminders, speech transcriber
server   Node/Express backend: auth, LLM parsing, Google Calendar + Notion
         execution, quota, RevenueCat webhooks (server/README.md)
```

The domain module has zero Android dependencies; everything below it is
swappable. The server degrades gracefully when integrations lack keys.

## Build & test

```bash
# Android (JDK 17)
gradlew.bat test          # 33 unit tests (JVM: parsers, quota, use cases, API)
gradlew.bat lint          # Android Lint — zero errors
gradlew.bat assembleDebug # debug APK (demo build)
gradlew.bat assembleRelease  # minified + shrunk + signed release APK

# Server
cd server
npm install
copy .env.example .env    # then fill in keys
npm run dev               # http://localhost:8787
```

Debug builds talk to `http://10.0.2.2:8787` (emulator loopback); release
builds point at the deployed server. Physical devices use the LAN IP.

## Design system

See [design.md](design.md) — colors, type scale, spacing, and component rules
(Apple Blue `#0071E3` is reserved for primary actions; surfaces separate by
hairline borders, never drop shadows; radii are exactly `8dp` or capsules).

## Docs

- [server/README.md](server/README.md) — API reference, rate limits, sessions