import { config, isGoogleCalendarConfigured } from '../config.js';
import { JWT, OAuth2Client } from 'google-auth-library';

async function getAccessToken() {
  if (config.google?.token) {
    return config.google.token;
  }

  try {
    if (config.google?.serviceAccountEmail) {
      const jwt = new JWT({
        email: config.google.serviceAccountEmail,
        key: config.google.serviceAccountKeyFile
          ? undefined
          : (config.google.serviceAccountKey || '').replace(/\\n/g, '\n'),
        keyFile: config.google.serviceAccountKeyFile || undefined,
        scopes: ['https://www.googleapis.com/auth/calendar'],
      });
      const { token } = await jwt.authorize();
      return token;
    }

    if (config.google?.refreshToken) {
      const oauth = new OAuth2Client(
        config.google.clientId,
        config.google.clientSecret,
      );
      oauth.setCredentials({ refresh_token: config.google.refreshToken });
      const { token } = await oauth.getAccessToken();
      return token;
    }
  } catch {
    return null;
  }

  return null;
}

/**
 * Creates a Google Calendar event via the Calendar v3 REST API.
 * Supports service-account and OAuth refresh-token flows.
 */
export async function createCalendarEvent(action) {
  try {
    if (!isGoogleCalendarConfigured()) {
      return {
        success: false,
        destination: 'google_calendar',
        message: 'Google Calendar not configured on the server',
      };
    }

    const accessToken = await getAccessToken();
    if (!accessToken) {
      return { success: false, destination: 'google_calendar', message: 'Google auth failed' };
    }

    const attendees = (action.attendees || []).map((a) => ({
      email: a.includes('@') ? a : `${a.toLowerCase().replace(/[^a-z0-9]/g, '.')}@actuate.local`,
      displayName: a.includes('@') ? undefined : a,
    }));

    const event = {
      summary: action.title || 'New event',
      start: action.all_day
        ? { date: (action.start || new Date().toISOString()).slice(0, 10) }
        : { dateTime: action.start || new Date().toISOString(), timeZone: 'UTC' },
      end: action.all_day
        ? { date: (action.end || action.start || new Date().toISOString()).slice(0, 10) }
        : { dateTime: action.end || action.start || new Date().toISOString(), timeZone: 'UTC' },
      description: action.description || undefined,
      location: action.location || undefined,
      attendees: attendees.length ? attendees : undefined,
    };

    const calendarId = config.google?.calendarId || 'primary';
    const apiBase = config.google?.apiBase || 'https://www.googleapis.com';
    const res = await fetch(
      `${apiBase}/calendar/v3/calendars/${encodeURIComponent(calendarId)}/events?sendUpdates=all`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(event),
      },
    );

    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      const message =
        res.status === 404
          ? 'Calendar not found — share it with the service account'
          : res.status === 403
            ? 'Calendar access denied'
            : `Google Calendar request failed: HTTP ${res.status}`;
      return { success: false, destination: 'google_calendar', message: `${message} ${detail}`.trim() };
    }

    const data = await res.json().catch(() => ({}));
    return {
      success: true,
      destination: 'google_calendar',
      eventId: data.id || null,
      message: `"${event.summary}" added to your Google calendar`,
    };
  } catch (err) {
    return {
      success: false,
      destination: 'google_calendar',
      message: `Google Calendar error: ${err.message}`,
    };
  }
}

/**
 * Deletes a remote Google Calendar event by its Google event ID so the
 * remote calendar stays in sync with Actuate deletions. Treats 404/410 as
 * success (already gone remotely).
 */
export async function deleteCalendarEvent(googleEventId) {
  if (!googleEventId) {
    return { success: false, destination: 'google_calendar', message: 'No Google event ID recorded' };
  }
  try {
    if (!isGoogleCalendarConfigured()) {
      return {
        success: false,
        destination: 'google_calendar',
        message: 'Google Calendar not configured on the server',
      };
    }

    const accessToken = await getAccessToken();
    if (!accessToken) {
      return { success: false, destination: 'google_calendar', message: 'Google auth failed' };
    }

    const calendarId = config.google?.calendarId || 'primary';
    const apiBase = config.google?.apiBase || 'https://www.googleapis.com';
    const res = await fetch(
      `${apiBase}/calendar/v3/calendars/${encodeURIComponent(calendarId)}/events/${encodeURIComponent(googleEventId)}`,
      {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${accessToken}` },
      },
    );

    if (res.status === 404 || res.status === 410) {
      return { success: true, destination: 'google_calendar', alreadyGone: true };
    }
    if (!res.ok) {
      return {
        success: false,
        destination: 'google_calendar',
        message: `Google Calendar delete failed: HTTP ${res.status}`,
      };
    }

    return { success: true, destination: 'google_calendar' };
  } catch (err) {
    return {
      success: false,
      destination: 'google_calendar',
      message: `Google Calendar delete error: ${err.message}`,
    };
  }
}
