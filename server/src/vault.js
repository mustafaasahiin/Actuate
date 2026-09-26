import { createCipheriv, createDecipheriv, createHmac, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { db } from './db.js';

/**
 * Encrypted credential vault for user-connected integrations.
 *
 * Secrets (SMTP passwords, WhatsApp access tokens) are encrypted at rest with
 * AES-256-GCM before they ever touch the DB file. The key is derived with
 * scrypt from MASTER_ENCRYPTION_KEY (or a fallback dev key) and a per-record
 * salt, so dumps of db.json alone reveal nothing usable.
 *
 * Each connection record keeps an HMAC integrity checksum so tampering with
 * the ciphertext in the DB file is detected at decrypt time.
 */

const FALLBACK_DEV_KEY = 'actuate-dev-only-insecure-master-key';

function masterKey() {
  const secret = process.env.MASTER_ENCRYPTION_KEY || FALLBACK_DEV_KEY;
  return scryptSync(secret, 'actuate-vault-salt', 32);
}

function encrypt(plaintext) {
  const iv = randomBytes(12);
  const salt = randomBytes(16);
  const key = scryptSync(masterKey(), salt, 32);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return `v1.${salt.toString('base64')}.${iv.toString('base64')}.${ciphertext.toString('base64')}.${authTag.toString('base64')}`;
}

function decrypt(payload) {
  if (typeof payload !== 'string') return null;
  const parts = payload.split('.');
  if (parts.length !== 5 || parts[0] !== 'v1') return null;
  try {
    const salt = Buffer.from(parts[1], 'base64');
    const iv = Buffer.from(parts[2], 'base64');
    const ciphertext = Buffer.from(parts[3], 'base64');
    const authTag = Buffer.from(parts[4], 'base64');
    const key = scryptSync(masterKey(), salt, 32);
    const decipher = createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);
    return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
  } catch {
    return null;
  }
}

function checksum(userKey, encrypted) {
  return createHmac('sha256', masterKey()).update(`${userKey}:${encrypted}`).digest('hex');
}

const collectionFor = (userId) => `connections.${userId}`;

function readConnections(userId) {
  return db.all(collectionFor(userId));
}

function writeConnection(userId, channel, record) {
  db.set(collectionFor(userId), channel, record);
}

function removeConnection(userId, channel) {
  db.remove(collectionFor(userId), channel);
}

/**
 * Persists credentials for a channel. Secrets are encrypted before storage;
 * the returned record never contains plaintext secrets.
 */
export function storeConnection(userId, channel, credentials) {
  const encrypted = encrypt(JSON.stringify(credentials));
  writeConnection(userId, channel, {
    channel,
    encrypted,
    integrity: checksum(`${userId}:${channel}`, encrypted),
    connectedAt: Date.now(),
  });
}

/**
 * Loads and decrypts credentials for a channel. Returns null when the
 * channel is not connected, the ciphertext was tampered with, or the
 * master key changed.
 */
export function loadConnection(userId, channel) {
  const record = db.get(collectionFor(userId), channel);
  if (!record) return null;
  if (record.integrity !== checksum(`${userId}:${record.channel || channel}`, record.encrypted)) {
    return null;
  }
  const decrypted = decrypt(record.encrypted);
  if (!decrypted) return null;
  try {
    return { ...JSON.parse(decrypted), connectedAt: record.connectedAt };
  } catch {
    return null;
  }
}

export function disconnectChannel(userId, channel) {
  removeConnection(userId, channel);
}

export function connectedChannels(userId) {
  const all = readConnections(userId);
  return Object.keys(all).filter((channel) => {
    const record = all[channel];
    if (!record) return false;
    return record.integrity === checksum(`${userId}:${record.channel || channel}`, record.encrypted) &&
      decrypt(record.encrypted) !== null;
  });
}

/** Never expose raw secrets to the client — only a masked hint. */
export function maskedStatus(userId, channel) {
  const record = db.get(collectionFor(userId), channel);
  if (!record) return { connected: false };
  const credentials = loadConnection(userId, channel);
  if (!credentials) {
    return { connected: false, corrupted: true };
  }
  const hint =
    channel === 'email'
      ? credentials.user || credentials.from || ''
      : credentials.phoneNumberId || '';
  return {
    connected: true,
    verified: Boolean(credentials.verified),
    connectedAt: record.connectedAt,
    hint: hint
      ? `${String(hint).slice(0, 2)}•••${String(hint).slice(-14).includes('@') ? '@' + String(hint).split('@')[1] : '•••'}`
      : '',
  };
}
