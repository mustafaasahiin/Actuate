import { rateLimit } from 'express-rate-limit';

function limiter(windowMs, limit, hint) {
  return rateLimit({
    windowMs,
    limit,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    handler: (_req, res) =>
      res.status(429).json({ error: hint, code: 'rate_limited' }),
  });
}

/** Global safety net — every IP, every route. */
export const globalLimiter = limiter(60 * 1000, 300, 'Too many requests — try again shortly.');

/** Registration spam guard (auto-register clients retry rarely). */
export const registerLimiter = limiter(60 * 60 * 1000, 5, 'Too many registration attempts — try again in an hour.');

/** LLM spend guard: parsing is the expensive call. */
export const parseLimiter = limiter(60 * 60 * 1000, 30, 'Too many parsing requests — try again in an hour.');

/** Execution is additionally capped by the per-user weekly quota. */
export const executeLimiter = limiter(60 * 60 * 1000, 120, 'Too many requests — try again later.');

export const webhookLimiter = limiter(60 * 1000, 60, 'Too many webhook events — try again shortly.');