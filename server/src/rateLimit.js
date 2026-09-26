import { rateLimit } from 'express-rate-limit';

function envInt(key, fallback) {
  const raw = Number(process.env[key]);
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
}

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
export const globalLimiter = limiter(
  60 * 1000,
  envInt('RATE_LIMIT_GLOBAL_MAX', 300),
  'Too many requests — try again shortly.',
);

export const registerLimiter = limiter(
  60 * 60 * 1000,
  envInt('RATE_LIMIT_AUTH_MAX', 15),
  'Too many registration attempts — try again in an hour.',
);

export const loginLimiter = limiter(
  15 * 60 * 1000,
  envInt('RATE_LIMIT_LOGIN_MAX', 10),
  'Too many login attempts — try again in 15 minutes.',
);

/** LLM spend guard: parsing is the expensive call. */
export const parseLimiter = limiter(
  60 * 60 * 1000,
  envInt('RATE_LIMIT_PARSE_MAX', 30),
  'Too many parsing requests — try again in an hour.',
);

/** Execution is additionally capped by the per-user weekly quota. */
export const executeLimiter = limiter(
  60 * 60 * 1000,
  envInt('RATE_LIMIT_EXECUTE_MAX', 120),
  'Too many requests — try again later.',
);

export const webhookLimiter = limiter(
  60 * 1000,
  envInt('RATE_LIMIT_WEBHOOK_MAX', 60),
  'Too many webhook events — try again shortly.',
);
