import 'dotenv/config';
import { fileURLToPath } from 'node:url';

const readJsonEnv = (key, fallback) => {
  const raw = process.env[key];
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
};

const readInt = (key, fallback) => {
  const raw = Number(process.env[key]);
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
};

const parsedNotionDatabases = () => {
  const fromEnv = readJsonEnv('NOTION_DATABASES', {});
  const generalFallback = process.env.NOTION_DATABASE_ID || process.env.NOTION_GENERAL_DATABASE_ID;
  if (!fromEnv.general && generalFallback) {
    return { ...fromEnv, general: generalFallback };
  }
  return fromEnv;
};

export const config = {
  port: readInt('PORT', 8787),
  host: process.env.HOST || '0.0.0.0',
  freeQuotaPerWeek: readInt('FREE_QUOTA_PER_WEEK', 20),
  proUserEmails: new Set(
    (process.env.PRO_USER_EMAILS || '')
      .split(',')
      .map((email) => email.trim().toLowerCase())
      .filter(Boolean),
  ),
  openrouter: {
    get apiKey() {
      return process.env.OPENROUTER_API_KEY || '';
    },
    get model() {
      return process.env.OPENROUTER_MODEL || 'openai/gpt-4o-mini';
    },
    get baseUrl() {
      return process.env.OPENROUTER_BASE_URL || 'https://openrouter.ai/api/v1';
    },
  },
  google: {
    get calendarId() {
      return process.env.GOOGLE_CALENDAR_ID || 'primary';
    },
    get apiBase() {
      return process.env.GOOGLE_CALENDAR_API_BASE || 'https://www.googleapis.com';
    },
    get token() {
      return process.env.GOOGLE_CALENDAR_TOKEN || '';
    },
    get serviceAccountEmail() {
      return (
        process.env.GOOGLE_CALENDAR_SERVICE_ACCOUNT_EMAIL ||
        process.env.GOOGLE_SERVICE_ACCOUNT_EMAIL ||
        ''
      );
    },
    get serviceAccountKey() {
      return (
        process.env.GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY ||
        process.env.GOOGLE_SERVICE_ACCOUNT_KEY ||
        ''
      );
    },
    get serviceAccountKeyFile() {
      return (
        process.env.GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY_FILE ||
        process.env.GOOGLE_SERVICE_ACCOUNT_KEY_FILE ||
        ''
      );
    },
    get clientId() {
      return (
        process.env.GOOGLE_CALENDAR_CLIENT_ID ||
        process.env.GOOGLE_CLIENT_ID ||
        ''
      );
    },
    get clientSecret() {
      return (
        process.env.GOOGLE_CALENDAR_CLIENT_SECRET ||
        process.env.GOOGLE_CLIENT_SECRET ||
        ''
      );
    },
    get refreshToken() {
      return (
        process.env.GOOGLE_CALENDAR_REFRESH_TOKEN ||
        process.env.GOOGLE_REFRESH_TOKEN ||
        ''
      );
    },
  },
  notion: {
    get token() {
      return process.env.NOTION_INTEGRATION_TOKEN || process.env.NOTION_TOKEN || '';
    },
    get apiBase() {
      return process.env.NOTION_API_BASE || 'https://api.notion.com/v1';
    },
    get databases() {
      return parsedNotionDatabases();
    },
    get enabled() {
      return isNotionConfigured();
    },
  },
  storage: {
    path: process.env.STORAGE_PATH || './data/items.json',
  },
  dbFile: process.env.DB_FILE || fileURLToPath(new URL('../data/db.json', import.meta.url)),
  revenuecatWebhookSecret: process.env.REVENUECAT_WEBHOOK_SECRET || '',
};

export const isGoogleCalendarConfigured = () => {
  if (process.env.GOOGLE_CALENDAR_ENABLED !== undefined) {
    return process.env.GOOGLE_CALENDAR_ENABLED === 'true';
  }
  return Boolean(
    config.google.token ||
    (config.google.serviceAccountEmail && (config.google.serviceAccountKey || config.google.serviceAccountKeyFile)) ||
    (config.google.refreshToken && (config.google.clientId || config.google.serviceAccountEmail))
  );
};

export const isNotionConfigured = () => {
  if (process.env.NOTION_ENABLED !== undefined) {
    return process.env.NOTION_ENABLED === 'true';
  }
  const dbs = config.notion.databases || {};
  return Boolean(
    config.notion.token &&
    (dbs.general || Object.keys(dbs).length > 0)
  );
};

export const integrationStatus = () => ({
  llm: Boolean(config.openrouter.apiKey),
  googleCalendar: isGoogleCalendarConfigured(),
  notion: isNotionConfigured(),
  storage: true,
});