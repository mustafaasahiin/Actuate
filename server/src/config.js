import 'dotenv/config';
import { fileURLToPath } from 'node:url';

const readJsonEnv = (key, fallback) => {
  const raw = process.env[key];
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch {
    throw new Error(`${key} must be valid JSON`);
  }
};

const readInt = (key, fallback) => {
  const raw = Number(process.env[key]);
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
};

export const config = {
  port: readInt('PORT', 8787),
  host: process.env.HOST || '0.0.0.0',
  freeQuotaPerWeek: readInt('FREE_QUOTA_PER_WEEK', 10),
  proUserEmails: new Set(
    (process.env.PRO_USER_EMAILS || '')
      .split(',')
      .map((email) => email.trim().toLowerCase())
      .filter(Boolean),
  ),
  openrouter: {
    apiKey: process.env.OPENROUTER_API_KEY || '',
    model: process.env.OPENROUTER_MODEL || 'openai/gpt-4o-mini',
    baseUrl: process.env.OPENROUTER_BASE_URL || 'https://openrouter.ai/api/v1',
  },
  notion: {
    enabled: Boolean(process.env.NOTION_INTEGRATION_TOKEN),
    token: process.env.NOTION_INTEGRATION_TOKEN || '',
    apiBase: process.env.NOTION_API_BASE || 'https://api.notion.com/v1',
    databases: readJsonEnv('NOTION_DATABASES', {}),
  },
  google: {
    serviceAccountEmail: process.env.GOOGLE_SERVICE_ACCOUNT_EMAIL || '',
    serviceAccountKey: process.env.GOOGLE_SERVICE_ACCOUNT_KEY || '',
    serviceAccountKeyFile: process.env.GOOGLE_SERVICE_ACCOUNT_KEY_FILE || '',
    calendarId: process.env.GOOGLE_CALENDAR_ID || 'primary',
    clientId: process.env.GOOGLE_CLIENT_ID || '',
    clientSecret: process.env.GOOGLE_CLIENT_SECRET || '',
    refreshToken: process.env.GOOGLE_REFRESH_TOKEN || '',
  },
  dbFile: process.env.DB_FILE || fileURLToPath(new URL('../data/db.json', import.meta.url)),
  revenuecatWebhookSecret: process.env.REVENUECAT_WEBHOOK_SECRET || '',
};

export const integrationStatus = () => ({
  llm: Boolean(config.openrouter.apiKey),
  notion: config.notion.enabled,
  google: Boolean(
    config.google.serviceAccountEmail ||
      config.google.refreshToken,
  ),
});