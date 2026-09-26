# Calendar Attendees & Just-In-Time Permission Architecture

## Overview

As part of the Actuate voice-to-action pipeline, calendar actions capture attendee information (`name` and optional `email`) end-to-end:
1. **Natural Language Parsing**: Extracted from speech transcripts (offline rule parser or server LLM parser).
2. **Domain Models**: Captured in `ParsedAction.Calendar` and `ServerActionItem` as `Attendee(name: String, email: String? = null)`.
3. **Local Persistence**: Serialized via `LocalActionItemDto` and `LocalAttendeeDto` into encrypted/local Jetpack DataStore preferences (`LocalActionStore`).
4. **Backend Server**: Persisted via `server/src/routes/actions.js` in SQLite/JSON database action records.
5. **Google Calendar API**: Synchronized to Google Calendar v3 events via `GoogleCalendarSyncService` with proper attendee payload structure (`email` and `displayName`).

Capturing attendee names and emails early prevents painful database migrations or model re-architectures when attendee-dependent features are rolled out.

---

## Architectural Guardrails: The Just-In-Time Permission Model

> **Roadmap constraint:** Meeting cancellation message drafting, approval, and send flows must NOT be built yet. When implemented in the future, permissions (Contacts access, SMS/WhatsApp sending) MUST be requested strictly just-in-time at the exact moment the user taps 'pick attendees' or chooses a send channel, with a clear one-line rationale, and NEVER requested upfront or during onboarding.

### Principles

1. **Zero Upfront Sensitive Permissions**
   - No `READ_CONTACTS`, `SEND_SMS`, or third-party messaging intents are declared or requested during onboarding or application launch.
   - Requesting sensitive permissions before immediate context violates Apple and Android Human Interface Guidelines, destroys user trust, and triggers automated app-store rejections.

2. **Just-In-Time Contextual Rationale**
   - When meeting cancellation messaging is implemented in a future milestone:
     - If the user explicitly taps "Pick from Contacts", the app displays a one-line explanation: *"Actuate needs Contacts access to find your attendee's phone number or email."*
     - If the user selects SMS delivery: *"Actuate needs SMS access to send your cancellation note."*
   - If the user denies permission, the app must gracefully offer manual input (typing the phone number or copying the message to the clipboard).

3. **No Automatic Sending Without Human Review**
   - Cancellation messages must never be sent automatically in the background without explicit user confirmation and review (Human-in-the-Loop approval gate).

---

## Data Model Specifications

### Domain Layer (`com.actuate.domain.model`)

```kotlin
data class Attendee(
    val name: String,
    val email: String? = null,
)

data class Calendar(
    override val id: String = UUID.randomUUID().toString(),
    val title: String,
    val start: Instant,
    val end: Instant?,
    val allDay: Boolean = false,
    val location: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val description: String? = null,
) : ParsedAction

data class ServerActionItem(
    val id: String,
    val type: String,
    val title: String,
    val at: Instant? = null,
    val end: Instant? = null,
    val location: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val description: String? = null,
    val pendingSync: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
```

### Storage Layer (`com.actuate.data.storage`)

```kotlin
@Serializable
internal data class LocalAttendeeDto(
    val name: String,
    val email: String? = null,
)

@Serializable
internal data class LocalActionItemDto(
    val id: String,
    val type: String,
    val title: String,
    val atEpochMilli: Long? = null,
    val endEpochMilli: Long? = null,
    val location: String? = null,
    val attendees: List<LocalAttendeeDto> = emptyList(),
    val description: String? = null,
    val pendingSync: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long = 0L,
)
```

### Google Calendar API Mapping (`GoogleCalendarSyncService.kt`)

Google Calendar API v3 requires each entry in `attendees` to specify an `email`. The payload builder handles both explicit email addresses and name-only attendees:

```kotlin
put("attendees", buildJsonArray {
    for (attendee in action.attendees) {
        val email = attendee.email
            ?: if (attendee.name.contains('@')) attendee.name
            else "${attendee.name.lowercase().replace(Regex("[^a-z0-9]"), ".")}@actuate.local"
        add(
            buildJsonObject {
                put("email", email)
                put("displayName", attendee.name)
            }
        )
    }
})
```

---

## Future Implementation Roadmap

When the cancellation flow is scheduled for implementation:
1. **UI Drafting Sheet**: Compose bottom sheet showing the proposed cancellation message, recipient attendee, and channel selector (Email, SMS, WhatsApp).
2. **Permission Gate**: Request `android.permission.READ_CONTACTS` or `android.permission.SEND_SMS` solely upon user interaction with the contact picker or send button.
3. **Draft Review**: Display editable message text before dispatch.
4. **Fallback Mechanism**: Provide intent launcher (`ACTION_SENDTO`) so messages can be sent through the system email/SMS app without needing direct runtime permissions.
