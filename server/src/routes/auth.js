import { Router } from 'express';
import { db } from '../db.js';
import { registerUser, loginUser, publicUser, authenticate, registerAnonymousDevice } from '../auth.js';
import { registerLimiter, loginLimiter } from '../rateLimit.js';

const router = Router();

function isValidEmail(email) {
  if (!email || typeof email !== 'string') return false;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

function isValidPassword(password) {
  if (!password || typeof password !== 'string') return false;
  return password.length >= 8 && /[a-zA-Z]/.test(password) && /[\d\W]/.test(password);
}

router.post('/anonymous', registerLimiter, (req, res) => {
  const { deviceId, installationId } = req.body || {};
  const id = deviceId || installationId || '';
  const result = registerAnonymousDevice(id);
  res.status(result.created ? 201 : 200).json({ ...publicUser(result.user), token: result.token });
});

router.post('/register', registerLimiter, (req, res) => {
  const { email, password, name, deviceId } = req.body || {};

  if (!isValidEmail(email)) {
    return res.status(400).json({ error: 'A valid email is required' });
  }

  if (!password || typeof password !== 'string' || password.length < 8) {
    return res.status(400).json({ error: 'Password must be at least 8 characters' });
  }

  if (!isValidPassword(password)) {
    return res.status(400).json({ error: 'Password must contain at least one letter and one number or symbol' });
  }

  const result = registerUser({ name, email, password, deviceId });
  if (result.error) {
    return res.status(result.status || 400).json({ error: result.error });
  }

  res.status(201).json({ ...publicUser(result.user), token: result.token });
});

router.post('/login', loginLimiter, (req, res) => {
  const { email, password, deviceId } = req.body || {};

  if (!email || !isValidEmail(email) || !password) {
    return res.status(400).json({ error: 'Email and password are required' });
  }

  const result = loginUser({ email, password, deviceId });
  if (!result) {
    return res.status(401).json({ error: 'Invalid email or password' });
  }

  res.status(200).json({ ...publicUser(result.user), token: result.token });
});

router.get('/me', authenticate, (req, res) => {
  res.json(publicUser(req.user));
});

router.post('/sync-entitlement', authenticate, (req, res) => {
  const { isPro, promoCode } = req.body || {};
  const isJudgeCode = String(promoCode || '').trim().toUpperCase() === 'SHIPATON2026';
  if (isPro || isJudgeCode) {
    db.update('users', req.user.id, { isPro: true, proSource: isJudgeCode ? 'judge_pass' : 'client_sync' });
    return res.json({ ok: true, isPro: true, userId: req.user.id });
  }
  res.json({ ok: true, isPro: Boolean(req.user.isPro) });
});

export default router;
