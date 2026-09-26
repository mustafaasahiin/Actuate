import { Router } from 'express';
import { authenticate } from '../auth.js';
import {
  storeConnection,
  loadConnection,
  disconnectChannel,
  connectedChannels,
  maskedStatus,
} from '../vault.js';
import { sendEmailViaUserConnection, sendWhatsAppViaUserConnection } from '../integrations/dispatch.js';

const router = Router();

router.use(authenticate);

const VALID_CHANNELS = new Set(['email', 'whatsapp']);

function isValidEmail(email) {
  if (!email || typeof email !== 'string') return false;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

/**
 * GET /api/v1/integrations
 * Connection status for the current user. Never returns secrets — only
 * connected/verified flags, timestamps, and a masked hint.
 */
router.get('/', (req, res) => {
  const channels = connectedChannels(req.user.id);
  const email = maskedStatus(req.user.id, 'email');
  const whatsapp = maskedStatus(req.user.id, 'whatsapp');

  res.json({
    email: {
      connected: email.connected,
      verified: Boolean(email.verified),
      connectedAt: email.connectedAt || null,
      hint: email.hint || '',
      corrupted: Boolean(email.corrupted),
    },
    whatsapp: {
      connected: whatsapp.connected,
      verified: Boolean(whatsapp.verified),
      connectedAt: whatsapp.connectedAt || null,
      hint: whatsapp.hint || '',
      corrupted: Boolean(whatsapp.corrupted),
    },
  });
});

/**
 * POST /api/v1/integrations/email
 * { host, port, user, password, from? }
 * Connects the user's SMTP account. Password is encrypted before storage.
 * Verification happens immediately: a live SMTP connection is opened.
 */
router.post('/email', async (req, res, next) => {
  try {
    const { host, port, user, password, from } = req.body || {};

    if (!host || typeof host !== 'string' || !host.includes('.')) {
      return res.status(400).json({ error: 'A valid SMTP host is required (e.g. smtp.gmail.com)' });
    }
    if (!isValidEmail(user)) {
      return res.status(400).json({ error: 'A valid email username is required' });
    }
    if (!password || typeof password !== 'string' || password.length < 8) {
      return res.status(400).json({ error: 'An SMTP password or app password is required' });
    }

    // Verify the credentials actually work before storing anything.
    const { default: nodemailer } = await import('nodemailer');
    const transporter = nodemailer.createTransport({
      host: host.trim(),
      port: Number(port) || 587,
      secure: Number(port) === 465,
      auth: { user: user.trim(), pass: password },
    });

    try {
      await transporter.verify();
    } catch (err) {
      return res.status(400).json({
        error: `Could not connect to ${host.trim()}: ${err.message || 'authentication failed'}`,
        code: 'smtp_verify_failed',
      });
    }

    storeConnection(req.user.id, 'email', {
      host: host.trim(),
      port: Number(port) || 587,
      user: user.trim(),
      password,
      from: (from && String(from).trim()) || user.trim(),
      verified: true,
    });

    res.json({ ok: true, channel: 'email', verified: true });
  } catch (err) {
    next(err);
  }
});

/**
 * POST /api/v1/integrations/whatsapp
 * { phoneNumberId, accessToken, displayNumber? }
 * Connects WhatsApp Cloud API credentials. Verified with a live Graph call.
 */
router.post('/whatsapp', async (req, res, next) => {
  try {
    const { phoneNumberId, accessToken, displayNumber } = req.body || {};

    if (!phoneNumberId || typeof phoneNumberId !== 'string' || !/^[0-9]{3,20}$/.test(phoneNumberId.trim())) {
      return res.status(400).json({ error: 'A valid WhatsApp phone number ID is required' });
    }
    if (!accessToken || typeof accessToken !== 'string' || accessToken.length < 20) {
      return res.status(400).json({ error: 'A WhatsApp access token is required' });
    }

    // Verify with a lightweight Graph call before storing anything.
    try {
      const verifyRes = await fetch(
        `https://graph.facebook.com/v21.0/${phoneNumberId.trim()}?access_token=${encodeURIComponent(accessToken.trim())}`,
      );
      if (!verifyRes.ok) {
        const detail = await verifyRes.text().catch(() => '');
        return res.status(400).json({
          error: `WhatsApp verification failed (HTTP ${verifyRes.status})`,
          code: 'whatsapp_verify_failed',
          detail: detail.slice(0, 200),
        });
      }
    } catch (err) {
      return res.status(400).json({
        error: `Could not reach WhatsApp Cloud API: ${err.message}`,
        code: 'whatsapp_verify_failed',
      });
    }

    storeConnection(req.user.id, 'whatsapp', {
      phoneNumberId: phoneNumberId.trim(),
      accessToken: accessToken.trim(),
      displayNumber: displayNumber ? String(displayNumber).trim() : '',
      verified: true,
    });

    res.json({ ok: true, channel: 'whatsapp', verified: true });
  } catch (err) {
    next(err);
  }
});

/**
 * POST /api/v1/integrations/:channel/test
 * Sends a short verification message through the connected channel.
 */
router.post('/:channel/test', async (req, res, next) => {
  try {
    const { channel } = req.params;
    if (!VALID_CHANNELS.has(channel)) {
      return res.status(400).json({ error: 'Unknown channel' });
    }

    const { recipient } = req.body || {};
    if (!recipient || typeof recipient !== 'string') {
      return res.status(400).json({ error: 'A test recipient is required' });
    }

    const body = 'Actuate: this is a test message confirming your connection works. No action needed.';
    const outcome = channel === 'email'
      ? await sendEmailViaUserConnection(req.user.id, { to: recipient, subject: 'Actuate connection test', body })
      : await sendWhatsAppViaUserConnection(req.user.id, { to: recipient, body });

    if (!outcome.sent) {
      return res.status(502).json({
        error: `Test send failed: ${outcome.reason || 'unknown'}`,
        code: 'test_send_failed',
        reason: outcome.reason,
      });
    }

    res.json({ ok: true, channel, messageId: outcome.messageId || null });
  } catch (err) {
    next(err);
  }
});

/**
 * DELETE /api/v1/integrations/:channel
 * Disconnects a channel and wipes its encrypted credentials.
 */
router.delete('/:channel', (req, res) => {
  const { channel } = req.params;
  if (!VALID_CHANNELS.has(channel)) {
    return res.status(400).json({ error: 'Unknown channel' });
  }
  disconnectChannel(req.user.id, channel);
  res.json({ ok: true, channel, connected: false });
});

export default router;
