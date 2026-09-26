import { Router } from 'express';
import { authenticate } from '../auth.js';
import { db } from '../db.js';
import { dispatchCancellation } from '../integrations/dispatch.js';
import { deleteCalendarEvent } from '../integrations/google-calendar.js';

const router = Router();

router.use(authenticate);

function cancellationEmailFor(title, whenText, message) {
  return {
    subject: `Cancelled: ${title}`,
    body: message,
  };
}

function formatEventWhen(existing) {
  const at = existing.at || existing.due_at;
  if (!at) return 'the originally scheduled time';
  try {
    const d = new Date(at);
    if (Number.isNaN(d.getTime())) return 'the originally scheduled time';
    return d.toLocaleString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  } catch {
    return 'the originally scheduled time';
  }
}

/**
 * Best-effort remote cleanup. If the event was created live on Google
 * Calendar, delete it there too so local cache, server DB, and Google
 * Calendar stay in sync. 404/410 on the remote side counts as success.
 */
async function deleteRemoteGoogleEvent(existing) {
  const googleEventId = existing.google_event_id || existing.eventId;
  if (!googleEventId) return { attempted: false };
  const result = await deleteCalendarEvent(googleEventId);
  return { attempted: true, ...result };
}

/**
 * POST /api/v1/calendar/:id/delete
 * Removes a calendar event from the user's action history.
 */
router.post('/:id/delete', async (req, res, next) => {
  try {
    const { id } = req.params;
    const collection = `actions.${req.user.id}`;
    const existing = db.get(collection, id);
    if (!existing) {
      return res.status(404).json({ error: 'Calendar event not found' });
    }
    if (existing.type !== 'calendar_event') {
      return res.status(400).json({ error: 'Not a calendar event' });
    }

    const remote = await deleteRemoteGoogleEvent(existing);
    db.remove(collection, id);
    return res.json({
      ok: true,
      deletedId: id,
      googleDeleted: remote.success === true,
      googleAttempted: remote.attempted === true,
    });
  } catch (err) {
    next(err);
  }
});

/**
 * POST /api/v1/calendar/:id/cancel-message  { email?, message }
 * Sends a cancellation message through the channels the user connected in
 * Settings (WhatsApp first, then email) and records the delivery outcome.
 * Falls back to audit-only logging when no channel is connected.
 */
router.post('/:id/cancel-message', async (req, res, next) => {
  try {
    const { id } = req.params;
    const { email, message } = req.body || {};
    const collection = `actions.${req.user.id}`;
    const existing = db.get(collection, id);
    if (!existing) {
      return res.status(404).json({ error: 'Calendar event not found' });
    }
    if (existing.type !== 'calendar_event') {
      return res.status(400).json({ error: 'Not a calendar event' });
    }
    if (typeof message !== 'string' || message.trim().length === 0) {
      return res.status(400).json({ error: 'A cancellation message is required' });
    }

    const recipientEmail = typeof email === 'string' && email.includes('@') ? email.trim() : null;
    if (!recipientEmail) {
      return res.status(400).json({ error: 'A valid attendee email is required to send a cancellation message' });
    }

    const whenText = formatEventWhen(existing);
    const title = existing.title || 'event';
    const emailPayload = cancellationEmailFor(title, whenText, message.trim());

    const dispatch = await dispatchCancellation(req.user.id, {
      to: recipientEmail,
      subject: emailPayload.subject,
      body: emailPayload.body,
    });

    const logKey = `cancellation_logs.${req.user.id}`;
    db.set(logKey, id, {
      eventId: id,
      title,
      email: recipientEmail,
      message: message.trim(),
      delivered: dispatch.delivered,
      channels: dispatch.outcomes.map((o) => ({ channel: o.channel, sent: o.sent, reason: o.reason || null })),
      createdAt: Date.now(),
    });

    return res.json({
      ok: true,
      eventId: id,
      delivered: dispatch.delivered,
      outcomes: dispatch.outcomes.map((o) => ({ channel: o.channel, sent: o.sent, reason: o.reason || null })),
    });
  } catch (err) {
    next(err);
  }
});

export default router;
