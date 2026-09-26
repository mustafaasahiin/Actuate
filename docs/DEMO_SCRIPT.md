# Actuate — judge demo script

**Total runtime: 60 seconds (extendable to 3 minutes).** Everything below has been
rehearsed on a physical arm64 device and on an `x86_64` emulator.

---

## Before you start (30 s setup, not part of the timer)

1. Install `app/build/outputs/apk/release/app-release.apk` (R8-minified, signed).
2. Grant the microphone permission when asked — the app asks at the moment of first use,
   not at launch.
3. Confirm dark mode. The Luminous Cyber-Editorial theme is the primary experience and is
   what the screenshots in the pitch deck show.
4. If a microphone is unavailable, jump to the **Zero-fail contingency** section.

---

## 0:00 – 0:15 — First boot and the living orb

| Say | Do | Expect |
|---|---|---|
| "This is Actuate — the intent execution layer." | Open the app. | Pitch-void obsidian canvas, a breathing cobalt orb with a cyan rim glow. |
| "Everything you're about to see runs locally." | Tap the orb and speak a sentence. | The orb swells with your voice and a **16-band radial waveform** expands around it — every bar is a real microphone RMS value, not a loop. |

> Deliberate detail: with the microphone muted the spectrum goes flat and falls back to the
> calm halo. It never fakes a signal.

## 0:15 – 0:45 — One thought, three actions, and the staging deck

Say exactly:

> **"Meeting with Priya at 3pm, buy eggs, and remind me at 8pm to send the invoice."**

| Step | What to point at |
|---|---|
| Sheet opens | The header reads **`[ 3 INTENTS COMPILED ]`** |
| Mode badge | **`LOCAL_RULE`** (or `CLOUD_LLM` if your server is up) — the app states which engine understood you |
| Latency pill | **`⚡ 42ms`** — a measured wall-clock parse time, not a placeholder |
| Three cards | Calendar (coral), List (indigo), Reminder (amber) — colour-coded by destination |
| Tap `[REMINDER]` on the meeting card | It re-routes live. The accent stripe and badge turn amber. **The sheet never closes and no other card is touched.** |
| Tap `+15m` twice | The timestamp advances by exactly 30 minutes. |
| Tap the title | It becomes an inline text field with a cyan underline — no modal, no keyboard-blocking dialog. |

## 0:45 – 1:15 — Offline resilience (the important 30 seconds)

1. Turn on **airplane mode**. Show the status bar.
2. Speak another compound sentence — for example *"buy bread and remind me at 7 to call the bank"*.
3. Watch it parse and stage exactly as before. **On-device `whisper.cpp` + the rule parser
   need no network.**
4. Commit. The reminder is scheduled through `AlarmManager`; the list item is persisted to
   DataStore optimistically and queued for Notion.

> The claim being demonstrated: capture, transcription, parsing and reminder scheduling all
> survive with the radio off. Only destination sync defers.

## 1:15 – 1:45 — Judge pass and the golden unlock

1. Open **Settings**.
2. Point out the diagnostics: **Whisper.cpp (ggml), on-device, ~57 MB**, the procedural
   sound engine, and the **Run latency test** button that measures a real round-trip.
3. Scroll to the plan section and enter judge code **`SHIPATON2026`**.

| Expect | |
|---|---|
| A golden confetti burst animates across the sheet (Canvas particles, seeded so two runs look identical) | |
| A four-beat haptic rumble plus the synthesized rising commit sweep | |
| A golden plaque: **`ACTUATE PRO · UNLIMITED JUDGE PASS`** | |

Quotas are lifted permanently for the session. No account, no card, no network.

---

## Zero-fail contingency table

| Failure | Cause | Recovery (do this, don't debug) |
|---|---|---|
| The orb never reacts to your voice | Emulator has no host microphone | Type the sentence into the thought field, press **Enter**, tap **Re-understand transcript**. The rest of the demo is identical. |
| Nothing transcribes after speaking | Microphone permission was denied earlier | Settings → app info → Permissions → Microphone → Allow. Then tap the orb again. |
| The server badge says `LOCAL_RULE` when you wanted `CLOUD_LLM` | Server unreachable or no LLM key | This is a **feature, not a failure** — say so out loud. The parse still produced every intent. |
| Sheet takes a moment on the first capture | Model warm-up on a cold process | Speak the next sentence; the second capture is noticeably faster. |
| Confetti does not appear | You typed the wrong code | The code is `SHIPATON2026` — all caps, no leading `#`. An invalid code deliberately plays the error buzz instead. |
| Wi-Fi is unavailable for the offline segment | Hall network | Airplane mode **is** the offline segment — it is strictly better than a flaky network for this demo. |
| A destination write fails | Notion/Calendar not connected on this device | The execution log marks it `FAIL` and offers **Retry failed actions**. Point out that the failing action is re-runnable while the successful ones are left alone. |

---

## Three-minute extended version

If you have more time, add these in order:

1. **Lists tab** — swipe an item right to complete it (emerald reveal + snap haptic), then
   swipe another left and tap **Undo** to restore it.
2. **History terminal** — open History, expand an entry, tap **Inspect parsed payload** and
   read the raw `actionType` / `destination` / `status` fields in monospace.
3. **Calendar** — show the sync pills switching between `[Live Google Calendar]` and
   `[Local cache]` as connectivity changes.
4. **Today radar** — show the hero card's live countdown and the hour-aware suggested
   prompt chips.

---

## What to say if a judge only asks one question

> "Every competitor transcribes in the cloud and stops at chat. We transcribe on the device,
> stage the result so you can correct it in one tap, and execute it — for zero marginal
> speech cost."
