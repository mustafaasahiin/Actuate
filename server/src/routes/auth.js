import { Router } from 'express';
import { registerUser, publicUser, authenticate } from '../auth.js';

const router = Router();

function validateEmail(email) {
  if (!email) return true;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/**
 * POST /api/v1/auth/register
 * { name?, email?, deviceId } -> { userId, token, ...publicUser }
 *
 * Upserts by deviceId: the same device always gets the same user row with a
 * fresh token, so the app can auto-register on every launch.
 */
router.post('/register', (req, res) => {
  const { name, email, deviceId } = req.body || {};
  if (!deviceId) {
    return res.status(400).json({ error: 'deviceId is required' });
  }
  if (!validateEmail(email)) {
    return res.status(400).json({ error: 'Invalid email address' });
  }
  const { user, token } = registerUser({ name, email, deviceId });
  res.status(201).json({ ...publicUser(user), token });
});

/**
 * GET /api/v1/auth/me  (bearer token)
 * Re-hydrates the session the app stored locally (offline re-login).
 */
router.get('/me', authenticate, (req, res) => {
  res.json(publicUser(req.user));
});

export default router;