import nodemailer from 'nodemailer';
import { loadConnection } from '../vault.js';

/**
 * Outbound message dispatch through user-connected channels.
 *
 * Email  : SMTP via STARTTLS/TLS using nodemailer (user's own credentials).
 * WhatsApp: WhatsApp Cloud API (Graph API) with the user's phone number ID
 *           and access token.
 *
 * Cancellation messages are only sent through channels the user explicitly
 * connected in Settings; nothing is dispatched to system-level accounts.
 */

const GRAPH_BASE = 'https://graph.facebook.com/v21.0';

export async function sendEmailViaUserConnection(userId, { to, subject, body }) {
  const connection = loadConnection(userId, 'email');
  if (!connection) {
    return { sent: false, channel: 'email', reason: 'not_connected' };
  }

  const transporter = nodemailer.createTransport({
    host: connection.host,
    port: Number(connection.port) || 587,
    secure: Number(connection.port) === 465,
    auth: {
      user: connection.user,
      pass: connection.password,
    },
  });

  try {
    const info = await transporter.sendMail({
      from: connection.from || connection.user,
      to,
      subject,
      text: body,
    });
    return { sent: true, channel: 'email', messageId: info?.messageId || null };
  } catch (err) {
    return { sent: false, channel: 'email', reason: err.message || 'smtp_error' };
  }
}

export async function sendWhatsAppViaUserConnection(userId, { to, body }) {
  const connection = loadConnection(userId, 'whatsapp');
  if (!connection) {
    return { sent: false, channel: 'whatsapp', reason: 'not_connected' };
  }

  const phoneNumberId = connection.phoneNumberId;
  const token = connection.accessToken;
  if (!phoneNumberId || !token) {
    return { sent: false, channel: 'whatsapp', reason: 'incomplete_credentials' };
  }

  // Recipient must be digits only, with country code, per Cloud API rules.
  const recipient = String(to).replace(/[^0-9]/g, '');
  if (!recipient) {
    return { sent: false, channel: 'whatsapp', reason: 'invalid_recipient' };
  }

  try {
    const res = await fetch(`${GRAPH_BASE}/${phoneNumberId}/messages`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: recipient,
        type: 'text',
        text: { preview_url: false, body },
      }),
    });

    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      return {
        sent: false,
        channel: 'whatsapp',
        reason: `whatsapp_api_http_${res.status}`,
        detail: detail.slice(0, 200),
      };
    }

    const data = await res.json().catch(() => ({}));
    const messageId = data?.messages?.[0]?.id || null;
    return { sent: true, channel: 'whatsapp', messageId };
  } catch (err) {
    return { sent: false, channel: 'whatsapp', reason: err.message || 'whatsapp_error' };
  }
}

/**
 * Sends a cancellation message through every channel the user connected,
 * stopping at the first success. Returns per-channel outcomes so the caller
 * can surface exactly what happened.
 */
export async function dispatchCancellation(userId, { to, subject, body }) {
  const outcomes = [];

  outcomes.push(await sendWhatsAppViaUserConnection(userId, { to, body }));
  outcomes.push(await sendEmailViaUserConnection(userId, { to, subject, body }));

  return {
    delivered: outcomes.some((o) => o.sent),
    outcomes,
  };
}
