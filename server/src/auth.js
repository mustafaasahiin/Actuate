import { randomBytes, randomUUID, scryptSync, timingSafeEqual } from 'node:crypto';
import { db } from './db.js';
import { config, integrationStatus } from './config.js';

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;

export function hashPassword(password) {
  const salt = randomBytes(16).toString('hex');
  const derivedKey = scryptSync(password, salt, 64);
  return `${salt}:${derivedKey.toString('hex')}`;
}

export function verifyPassword(password, storedHash) {
  if (!storedHash || typeof storedHash !== 'string' || !storedHash.includes(':')) {
    return false;
  }
  const [salt, key] = storedHash.split(':');
  try {
    const keyBuffer = Buffer.from(key, 'hex');
    const derivedKey = scryptSync(password, salt, 64);
    if (keyBuffer.length !== derivedKey.length) return false;
    return timingSafeEqual(keyBuffer, derivedKey);
  } catch {
    return false;
  }
}

export function newToken() {
  return randomBytes(24).toString('base64url');
}

function userTokens(user) {
  return (user.tokens || []).map((t) =>
    typeof t === 'string'
      ? { value: t, deviceId: '', createdAt: 0, expiresAt: Date.now() + TOKEN_TTL_MS }
      : { ...t, deviceId: t.deviceId || '' },
  );
}

function pruneExpiredTokens(user) {
  const now = Date.now();
  const live = userTokens(user).filter((t) => !t.expiresAt || t.expiresAt > now);
  if (live.length !== (user.tokens || []).length) {
    db.update('users', user.id, { tokens: live });
  }
}

export function registerUser({ name, email, password, deviceId, installationId }) {
  const normalizedEmail = String(email || '').trim().toLowerCase();
  const effectiveDeviceId = String(deviceId || installationId || '').trim();
  
  if (normalizedEmail) {
    const existing = db.all('users').find((u) => u.email === normalizedEmail);
    if (existing) {
      return { error: 'An account with this email already exists', status: 409 };
    }
  }

  const token = {
    value: newToken(),
    deviceId: effectiveDeviceId,
    createdAt: Date.now(),
    expiresAt: Date.now() + TOKEN_TTL_MS,
  };

  const id = randomUUID();
  const isPro = config.proUserEmails.has(normalizedEmail);
  const passwordHash = password ? hashPassword(password) : '';

  const user = {
    id,
    name: name || '',
    email: normalizedEmail,
    passwordHash,
    deviceId: effectiveDeviceId,
    isPro,
    createdAt: Date.now(),
    tokens: [token],
    quotaTimestamps: [],
  };
  db.set('users', id, user);
  return { user, token: token.value, created: true };
}

export function registerAnonymousDevice(installationId) {
  const effectiveId = String(installationId || '').trim() || randomUUID();
  const existing = db.all('users').find((u) => !u.email && u.deviceId === effectiveId);
  if (existing) {
    const token = {
      value: newToken(),
      deviceId: effectiveId,
      createdAt: Date.now(),
      expiresAt: Date.now() + TOKEN_TTL_MS,
    };
    const now = Date.now();
    const existingTokens = userTokens(existing).filter((t) => !t.expiresAt || t.expiresAt > now);
    const updated = db.update('users', existing.id, {
      tokens: [...existingTokens, token],
      updatedAt: now,
    });
    return { user: updated, token: token.value, created: false };
  }

  return registerUser({
    name: 'Anonymous',
    deviceId: effectiveId,
  });
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

export function loginUser({ email, password, deviceId, installationId }) {
  const normalized = String(email || '').trim().toLowerCase();
  if (!normalized || !password) return null;

  const account = db.all('users').find((u) => u.email === normalized);
  if (!account || !account.passwordHash) return null;

  const isValid = verifyPassword(password, account.passwordHash);
  if (!isValid) return null;

  const effectiveDeviceId = String(deviceId || installationId || '').trim();

  if (effectiveDeviceId) {
    for (const other of db.all('users')) {
      if (other.id !== account.id && other.deviceId === effectiveDeviceId) {
        db.update('users', other.id, { deviceId: '' });
      }
    }
  }

  const now = Date.now();
  const token = {
    value: newToken(),
    deviceId: effectiveDeviceId,
    createdAt: now,
    expiresAt: now + TOKEN_TTL_MS,
  };

  const existingTokens = userTokens(account).filter((t) => !t.expiresAt || t.expiresAt > now);
  const updatedTokens = [
    ...existingTokens.filter((t) => !effectiveDeviceId || t.deviceId !== effectiveDeviceId),
    token,
  ];

  const patch = {
    tokens: updatedTokens,
    updatedAt: now,
  };
  if (effectiveDeviceId) {
    patch.deviceId = effectiveDeviceId;
  }
  const updated = db.update('users', account.id, patch);
  return { user: updated, token: token.value };
}

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
