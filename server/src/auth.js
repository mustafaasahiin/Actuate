import { randomBytes, randomUUID } from 'node:crypto';
import { db } from './db.js';
import { config, integrationStatus } from './config.js';

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;

export function newToken() {
  return randomBytes(24).toString('base64url');
}

/**
 * Normalizes legacy plain-string tokens from older db.json files into
 * { value, createdAt, expiresAt } objects (they effectively get a fresh
 * 30-day TTL on first read).
 */
function userTokens(user) {
  return (user.tokens || []).map((t) =>
    typeof t === 'string'
      ? { value: t, createdAt: 0, expiresAt: Date.now() + TOKEN_TTL_MS }
      : t,
  );
}

function pruneExpiredTokens(user) {
  const now = Date.now();
  const live = userTokens(user).filter((t) => !t.expiresAt || t.expiresAt > now);
  if (live.length !== (user.tokens || []).length) {
    db.update('users', user.id, { tokens: live });
  }
}

/**
 * Registers a new user, or re-issues a token for the same device
 * (upsert by deviceId). The app auto-registers on every launch, so this
 * keeps one user row per device instead of unbounded duplicates.
 */
export function registerUser({ name, email, deviceId }) {
  const existing = deviceId
    ? db.all('users').find((u) => u.deviceId === deviceId)
    : null;
  const token = {
    value: newToken(),
    createdAt: Date.now(),
    expiresAt: Date.now() + TOKEN_TTL_MS,
  };

  if (existing) {
    pruneExpiredTokens(existing);
    // Session rotation: each re-register (every app launch) replaces the
    // previous token, so a leaked token stops working on the next launch.
    const patch = { tokens: [token], updatedAt: Date.now() };
    if (name) patch.name = name;
    if (email) patch.email = String(email).toLowerCase();
    const updated = db.update('users', existing.id, patch);
    return { user: updated, token: token.value, created: false };
  }

  const id = randomUUID();
  const isPro = config.proUserEmails.has(String(email || '').toLowerCase());
  const user = {
    id,
    name: name || '',
    email: (email || '').toLowerCase(),
    deviceId: deviceId || '',
    isPro,
    createdAt: Date.now(),
    tokens: [token],
    quotaTimestamps: [],
  };
  db.set('users', id, user);
  return { user, token: token.value, created: true };
}

export function authenticate(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token) {
    return res.status(401).json({ error: 'Missing bearer token' });
  }
  const now = Date.now();
  const user = db.all('users').find((u) =>
    userTokens(u).some((t) => t.value === token && (!t.expiresAt || t.expiresAt > now)),
  );
  if (!user) {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
  pruneExpiredTokens(user);
  req.user = user;
  next();
}

/**
 * Rolling 7-day window quota. Pro users are unlimited.
 * Returns the timestamps actually stored after consuming `count` slots.
 */
export function consumeQuota(user, count) {
  if (user.isPro) return { ok: true, used: [] };
  const now = Date.now();
  const cutoff = now - WEEK_MS;
  const recent = (user.quotaTimestamps || []).filter((ts) => ts >= cutoff);
  const used = recent.length;
  const free = config.freeQuotaPerWeek;
  if (used + count > free) {
    return { ok: false, used: recent };
  }
  for (let i = 0; i < count; i += 1) {
    recent.push(now);
  }
  db.update('users', user.id, { quotaTimestamps: recent });
  return { ok: true, used: recent };
}

export function quotaRemaining(user) {
  if (user.isPro) return null;
  // Read fresh from the store so a just-consumed quota is reflected
  // immediately in the same response.
  const fresh = db.get('users', user.id) || user;
  const cutoff = Date.now() - WEEK_MS;
  const used = (fresh.quotaTimestamps || []).filter((ts) => ts >= cutoff).length;
  return Math.max(0, config.freeQuotaPerWeek - used);
}

export function publicUser(user) {
  return {
    userId: user.id,
    name: user.name,
    email: user.email,
    isPro: user.isPro,
    quotaLimit: user.isPro ? null : config.freeQuotaPerWeek,
    quotaRemaining: quotaRemaining(user),
    integrations: integrationStatus(),
  };
}