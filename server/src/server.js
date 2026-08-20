import express from 'express';
import { initDb, db } from './db.js';
import { config } from './config.js';
import {
  globalLimiter,
  registerLimiter,
  parseLimiter,
  executeLimiter,
  webhookLimiter,
} from './rateLimit.js';
import healthRouter from './routes/health.js';
import authRouter from './routes/auth.js';
import actionsRouter from './routes/actions.js';
import webhooksRouter from './routes/webhooks.js';

const app = express();

app.use(express.json({
  limit: '256kb',
  verify: (req, _res, buf) => {
    req.rawBody = buf; // needed for RevenueCat webhook signature validation
  },
}));
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

// Rate limiting for all users, applied before routing.
app.use(globalLimiter);
app.use('/api/v1/auth/register', registerLimiter);
app.use('/api/v1/actions/parse', parseLimiter);
app.use('/api/v1/actions/execute', executeLimiter);
app.use('/api/v1/webhooks', webhookLimiter);

app.use('/', healthRouter);
app.use('/api/v1/auth', authRouter);
app.use('/api/v1/actions', actionsRouter);
app.use('/api/v1/webhooks', webhooksRouter);

app.use((req, res) => {
  res.status(404).json({ error: `Not found: ${req.method} ${req.path}` });
});

app.use((err, _req, res, _next) => {
  console.error('[actuate-server]', err);
  // Never leak internal messages (LLM/provider errors, stack traces) to
  // clients; only stable error codes survive.
  res.status(500).json({ error: 'Internal server error' });
});

initDb(config.dbFile);
app.listen(config.port, config.host, () => {
  console.log(`Actuate server listening on http://${config.host}:${config.port}`);
  console.log(`  DB: ${config.dbFile}`);
});