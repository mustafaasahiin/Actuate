import { createHmac } from 'node:crypto';
import { Router } from 'express';
import { db } from '../db.js';
import { config } from '../config.js';

const router = Router();

/**
 * POST /api/v1/webhooks/revenuecat
 * RevenueCat webhook that flips a user to Pro when the entitlement is active.
 * When REVENUECAT_WEBHOOK_SECRET is set, x-request-signature (HMAC-SHA256 of
 * the raw body) is required and verified.
 */
router.post('/revenuecat', (req, res) => {
  const secret = config.revenuecatWebhookSecret;
  if (secret) {
    const signature = req.headers['x-request-signature'] || '';
    const expected = createHmac('sha256', secret)
      .update(req.rawBody || '')
      .digest('base64');
    if (!signature || signature !== expected) {
      return res.status(401).json({ error: 'Invalid webhook signature' });
    }
  }

  const { event } = req.body || {};
  if (!event || !event.app_user_id) {
    return res.status(400).json({ error: 'Missing event or app_user_id' });
  }

  const id = String(event.app_user_id).toLowerCase();
  const isActive =
    event.type === 'INITIAL_PURCHASE' ||
    event.type === 'RENEWAL' ||
    event.type === 'UNCANCELLATION' ||
    event.type === 'PRODUCT_CHANGE' ||
    (event.type === 'CANCELLATION' && event.cancellation_type === 'BILLING_ERROR');
  const isNonRenewing =
    event.type === 'NON_RENEWING_PURCHASE';

  // app_user_id is the device id registered by the app (unique per install).
  const findUser = () => db.all('users').find(
    (u) => u.email === id || u.id === id || String(u.deviceId || '').toLowerCase() === id,
  );

  if (isActive || isNonRenewing) {
    const user = findUser();
    if (user) {
      db.update('users', user.id, { isPro: true, proSource: 'revenuecat' });
      console.log(`[webhook] ${id} upgraded to Pro`);
      return res.json({ ok: true, userId: user.id, isPro: true });
    }
  } else if (event.type === 'EXPIRATION') {
    const user = findUser();
    if (user) {
      db.update('users', user.id, { isPro: false, proSource: null });
      return res.json({ ok: true, userId: user.id, isPro: false });
    }
  }

  res.json({ ok: true, matched: false });
});

export default router;