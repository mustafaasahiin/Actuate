import { config } from '../config.js';
import { JWT, OAuth2Client } from 'google-auth-library';

async function getAccessToken() {
  if (config.google.serviceAccountEmail) {
    const jwt = new JWT({
      email: config.google.serviceAccountEmail,
      key: config.google.serviceAccountKeyFile
        ? undefined
        : config.google.serviceAccountKey.replace(/\\n/g, '\n'),
      keyFile: config.google.serviceAccountKeyFile || undefined,
      scopes: ['https://www.googleapis.com/auth/calendar'],
    });
    const { token } = await jwt.authorize();
    return token;
  }

  if (config.google.refreshToken) {
    const oauth = new OAuth2Client(
      config.google.clientId,
      config.google.clientSecret,
    );
    oauth.setCredentials({ refresh_token: config.google.refreshToken });
    const { token } = await oauth.getAccessToken();
    return token;
  }

  return null;
}

/**
 * Creates a Google Calendar event via the Calendar v3 REST API.
 * Supports service-account and OAuth refresh-token flows.
 */
export async function createCalendarEvent(action) {
  if (!config.google.serviceAccountEmail && !config.google.refreshToken) {
    return {
      success: false,
      destination: 'calendar',
      message: 'Google Calendar not configured on the server',
    };
  }

  const accessToken = await getAccessToken();
  if (!accessToken) {
    return { success: false, destination: 'calendar', message: 'Google auth failed' };
  }

  const attendees = (action.attendees || []).map((a) => ({
    email: a.includes('@') ? a : `${a.toLowerCase().replace(/[^a-z0-9]/g, '.')}@actuate.local`,
    displayName: a.includes('@') ? undefined : a,
  }));

  const event = {
    summary: action.title || 'New event',
    start: action.all_day
      ? { date: (action.start || new Date().toISOString()).slice(0, 10) }
      : { dateTime: action.start, timeZone: 'UTC' },
    end: action.all_day
      ? { date: (action.end || action.start || new Date().toISOString()).slice(0, 10) }
      : { dateTime: action.end || action.start, timeZone: 'UTC' },
    description: action.description || undefined,
    location: action.location || undefined,
    attendees: attendees.length ? attendees : undefined,
  };

  const res = await fetch(
    `https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(config.google.calendarId)}/events?sendUpdates=all`,
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
    return { success: false, destination: 'calendar', message: `${message} ${detail}`.trim() };
  }

  return { success: true, destination: 'calendar', message: `"${event.summary}" added to your Google calendar` };
}