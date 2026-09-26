import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac, randomUUID } from 'node:crypto';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import http from 'node:http';

// --- Env must be set BEFORE the app modules load (config reads at import).
process.env.DB_FILE = join(mkdtempSync(join(tmpdir(), 'actuate-test-')), 'db.json');
process.env.REVENUECAT_WEBHOOK_SECRET = 'test-webhook-secret';
process.env.PRO_USER_EMAILS = 'pro@test.dev,pro-frees@test.dev';
process.env.RATE_LIMIT_GLOBAL_MAX = '100000';
process.env.RATE_LIMIT_AUTH_MAX = '100000';
process.env.RATE_LIMIT_PARSE_MAX = '100000';
process.env.RATE_LIMIT_EXECUTE_MAX = '100000';
process.env.RATE_LIMIT_WEBHOOK_MAX = '100000';

const { createApp } = await import('../src/server.js');
const { initDb, db } = await import('../src/db.js');
const { config } = await import('../src/config.js');

let server;
let base;

before(async () => {
  initDb(config.dbFile);
  await new Promise((resolve) => {
    server = createApp().listen(0, '127.0.0.1', resolve);
  });
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => new Promise((resolve) => server.close(resolve)));

const post = async (path, body, token) => {
  const res = await fetch(`${base}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: res.status, json: await res.json().catch(() => ({})) };
};

const get = async (path, token) => {
  const res = await fetch(`${base}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  return { status: res.status, json: await res.json().catch(() => ({})) };
};

const registerDevice = async (deviceId, extra = {}) => {
  const email = extra.email || `user-${deviceId.slice(0, 8)}@test.dev`;
  const password = extra.password || 'Password123!';
  return post('/api/v1/auth/register', { deviceId, email, password, ...extra });
};

// --- Health -----------------------------------------------------------------

test('GET /health reports ok and the free quota of 20', async () => {
  const res = await get('/health');
  assert.equal(res.status, 200);
  assert.equal(res.json.ok, true);
  assert.equal(res.json.freeQuotaPerWeek, 20);
  assert.deepEqual(res.json.integrations, {
    llm: false,
    googleCalendar: false,
    notion: false,
    storage: true,
  });

  process.env.GOOGLE_CALENDAR_ENABLED = 'true';
  process.env.NOTION_ENABLED = 'true';
  const configured = await get('/health');
  assert.equal(configured.json.integrations.googleCalendar, true);
  assert.equal(configured.json.integrations.notion, true);
  delete process.env.GOOGLE_CALENDAR_ENABLED;
  delete process.env.NOTION_ENABLED;

  process.env.GOOGLE_SERVICE_ACCOUNT_EMAIL = 'svc@example.iam.gserviceaccount.com';
  process.env.GOOGLE_SERVICE_ACCOUNT_KEY = 'fake-key';
  process.env.NOTION_INTEGRATION_TOKEN = 'secret_notion_tok';
  process.env.NOTION_DATABASES = '{"general":"db_123"}';
  const credsConfigured = await get('/health');
  assert.equal(credsConfigured.json.integrations.googleCalendar, true);
  assert.equal(credsConfigured.json.integrations.notion, true);
  delete process.env.GOOGLE_SERVICE_ACCOUNT_EMAIL;
  delete process.env.GOOGLE_SERVICE_ACCOUNT_KEY;
  delete process.env.NOTION_INTEGRATION_TOKEN;
  delete process.env.NOTION_DATABASES;
});

// --- Register ---------------------------------------------------------------

test('register requires a valid email', async () => {
  const noEmail = await post('/api/v1/auth/register', { password: 'Password123!' });
  assert.equal(noEmail.status, 400);

  const badEmail = await post('/api/v1/auth/register', { email: 'not-an-email', password: 'Password123!' });
  assert.equal(badEmail.status, 400);
});

test('register rejects weak passwords (< 8 characters or no number/symbol)', async () => {
  const shortPw = await post('/api/v1/auth/register', { email: 'user-pw1@test.dev', password: 'Pass1!' });
  assert.equal(shortPw.status, 400);

  const noNumberPw = await post('/api/v1/auth/register', { email: 'user-pw2@test.dev', password: 'PasswordOnly' });
  assert.equal(noNumberPw.status, 400);
});

test('register creates a user with a 20-per-week free quota and never leaks password', async () => {
  const email = 'ada@test.dev';
  const rawPassword = 'Password123!';
  const res = await post('/api/v1/auth/register', { name: 'Ada', email, password: rawPassword });
  assert.equal(res.status, 201);
  assert.ok(res.json.token);
  assert.equal(res.json.isPro, false);
  assert.equal(res.json.quotaLimit, 20);
  assert.equal(res.json.quotaRemaining, 20);
  assert.equal(res.json.password, undefined);
  assert.equal(res.json.passwordHash, undefined);

  const stored = db.all('users').find((u) => u.email === email);
  assert.ok(stored.passwordHash);
  assert.notEqual(stored.passwordHash, rawPassword);
  assert.ok(stored.passwordHash.includes(':'));
});

test('register rejects duplicate email with 409 Conflict', async () => {
  const email = 'duplicate@test.dev';
  const first = await post('/api/v1/auth/register', { email, password: 'Password123!' });
  assert.equal(first.status, 201);

  const duplicate = await post('/api/v1/auth/register', { email, password: 'Password123!' });
  assert.equal(duplicate.status, 409);
  assert.match(duplicate.json.error, /already exists/i);
});

test('login validates input and rejects missing credentials', async () => {
  assert.equal((await post('/api/v1/auth/login', { email: 'x@y.dev' })).status, 400);
  assert.equal((await post('/api/v1/auth/login', { password: 'Password123!' })).status, 400);
  assert.equal((await post('/api/v1/auth/login', { email: 'not-an-email', password: 'Password123!' })).status, 400);
});

test('login rejects unknown emails and wrong passwords with 401 Unauthorized', async () => {
  const unknown = await post('/api/v1/auth/login', {
    email: 'ghost@test.dev',
    password: 'Password123!',
  });
  assert.equal(unknown.status, 401);
  assert.match(unknown.json.error, /Invalid email or password/i);

  await post('/api/v1/auth/register', { email: 'bob@test.dev', password: 'CorrectPassword1!' });
  const wrongPw = await post('/api/v1/auth/login', {
    email: 'bob@test.dev',
    password: 'WrongPassword1!',
  });
  assert.equal(wrongPw.status, 401);
  assert.match(wrongPw.json.error, /Invalid email or password/i);
});

test('login succeeds with correct credentials and returns working token', async () => {
  const email = 'grace@test.dev';
  const password = 'Password123!';
  const originalDevice = randomUUID();
  const reg = await post('/api/v1/auth/register', {
    name: 'Grace',
    email,
    password,
    deviceId: originalDevice,
  });
  assert.equal(reg.status, 201);

  for (let i = 0; i < 2; i += 1) {
    await post('/api/v1/actions/execute', {
      actions: [{ id: `r${i}`, type: 'reminder', title: 'Stretch' }],
    }, reg.json.token);
  }

  const newDevice = randomUUID();
  const login = await post('/api/v1/auth/login', {
    email,
    password,
    deviceId: newDevice,
  });
  assert.equal(login.status, 200);
  assert.equal(login.json.quotaRemaining, 18);
  assert.equal(login.json.password, undefined);

  const me = await get('/api/v1/auth/me', login.json.token);
  assert.equal(me.status, 200);
  assert.equal(me.json.email, email);
});

test('multi-device session isolation: device A token remains valid when device B logs in', async () => {
  const email = 'multidevice@test.dev';
  const password = 'Password123!';
  const deviceA = randomUUID();
  const reg = await post('/api/v1/auth/register', {
    email,
    password,
    deviceId: deviceA,
  });
  assert.equal(reg.status, 201);
  const tokenA = reg.json.token;

  const deviceB = randomUUID();
  const loginB = await post('/api/v1/auth/login', {
    email,
    password,
    deviceId: deviceB,
  });
  assert.equal(loginB.status, 200);
  const tokenB = loginB.json.token;
  assert.notEqual(tokenA, tokenB);

  const meA = await get('/api/v1/auth/me', tokenA);
  assert.equal(meA.status, 200);
  assert.equal(meA.json.email, email);

  const meB = await get('/api/v1/auth/me', tokenB);
  assert.equal(meB.status, 200);
  assert.equal(meB.json.email, email);
});

test('POST /api/v1/auth/anonymous issues isolated session for installation ID', async () => {
  const installId = randomUUID();
  const res = await post('/api/v1/auth/anonymous', { installationId: installId });
  assert.equal(res.status, 201);
  assert.ok(res.json.token);
  assert.equal(res.json.isPro, false);
  assert.equal(res.json.quotaRemaining, 20);

  const me = await get('/api/v1/auth/me', res.json.token);
  assert.equal(me.status, 200);
  assert.equal(me.json.quotaRemaining, 20);
});

test('POST /api/v1/auth/sync-entitlement upgrades user to Pro on client sync or promo pass', async () => {
  const { json } = await registerDevice(randomUUID());
  const token = json.token;

  const sync = await post('/api/v1/auth/sync-entitlement', { promoCode: 'SHIPATON2026' }, token);
  assert.equal(sync.status, 200);
  assert.equal(sync.json.isPro, true);

  const me = await get('/api/v1/auth/me', token);
  assert.equal(me.status, 200);
  assert.equal(me.json.isPro, true);
  assert.equal(me.json.quotaRemaining, null);
});

test('login frees the device from its previous anonymous row', async () => {
  await registerDevice(randomUUID(), { name: 'Pro', email: 'pro-frees@test.dev' });

  const device = randomUUID();
  await registerDevice(device);
  const pro = await post('/api/v1/auth/login', {
    deviceId: device,
    email: 'pro-frees@test.dev',
    password: 'Password123!',
  });
  assert.equal(pro.status, 200);
  assert.equal(pro.json.isPro, true);
  assert.equal(pro.json.quotaRemaining, null);

  const me = await get('/api/v1/auth/me', pro.json.token);
  assert.equal(me.json.userId, pro.json.userId);
  assert.equal(me.json.isPro, true);
});

// --- Actions & quota --------------------------------------------------------

test('actions require authentication', async () => {
  const res = await get('/api/v1/actions');
  assert.equal(res.status, 401);
});

test('free tier allows exactly 20 actions per rolling week', async () => {
  const { json } = await registerDevice(randomUUID());
  const runAction = (id) => post('/api/v1/actions/execute', {
    actions: [{ id, type: 'reminder', title: `Reminder ${id}` }],
  }, json.token);

  const first = await runAction('a1');
  assert.equal(first.status, 200);
  assert.equal(first.json.quotaRemaining, 19);

  for (let i = 2; i <= 20; i += 1) {
    const res = await runAction(`a${i}`);
    assert.equal(res.status, 200);
  }

  const twentyFirst = await runAction('a21');
  assert.equal(twentyFirst.status, 429);
  assert.equal(twentyFirst.json.code, 'quota_exceeded');
  assert.equal(twentyFirst.json.quotaRemaining, 0);
  assert.match(twentyFirst.json.error, /20 actions per week/);
});

test('pro users bypass the weekly quota', async () => {
  // Seed the Pro account (PRO_USER_EMAILS grants isPro at creation), then
  // claim it on a fresh device via login.
  await registerDevice(randomUUID(), { name: 'Pro', email: 'pro@test.dev' });
  const device = randomUUID();
  await registerDevice(device);
  const login = await post('/api/v1/auth/login', {
    deviceId: device,
    email: 'pro@test.dev',
    password: 'Password123!',
  });
  assert.equal(login.status, 200);
  assert.equal(login.json.isPro, true);
  const runAction = (id) => post('/api/v1/actions/execute', {
    actions: [{ id, type: 'reminder', title: `Reminder ${id}` }],
  }, login.json.token);
  for (let i = 0; i < 5; i += 1) {
    const res = await runAction(`p${i}`);
    assert.equal(res.status, 200);
    assert.equal(res.json.quotaRemaining, null);
  }
});

test('execute rejects empty and oversized action batches', async () => {
  const { json } = await registerDevice(randomUUID());
  const empty = await post('/api/v1/actions/execute', { actions: [] }, json.token);
  assert.equal(empty.status, 400);
  const tooMany = await post('/api/v1/actions/execute', {
    actions: Array.from({ length: 26 }, (_, i) => ({ id: `${i}`, type: 'reminder', title: 'x' })),
  }, json.token);
  assert.equal(tooMany.status, 400);
  assert.match(tooMany.json.error, /max 25/i);
});

// --- RevenueCat webhook ------------------------------------------------------

function signedWebhook(event) {
  const body = JSON.stringify({ event });
  return createHmac('sha256', process.env.REVENUECAT_WEBHOOK_SECRET)
    .update(body)
    .digest('base64');
}

const webhookPost = async (payload, signature) => {
  const res = await fetch(`${base}/api/v1/webhooks/revenuecat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(signature ? { 'x-request-signature': signature } : {}),
    },
    body: JSON.stringify(payload),
  });
  return { status: res.status, json: await res.json().catch(() => ({})) };
};

test('webhook rejects missing or invalid signatures', async () => {
  const payload = { event: { type: 'INITIAL_PURCHASE', app_user_id: 'someone' } };
  assert.equal((await webhookPost(payload)).status, 401);
  assert.equal((await webhookPost(payload, 'bad-signature')).status, 401);
});

test('webhook upgrades a matched device to Pro and expiration downgrades it', async () => {
  const device = randomUUID();
  const reg = await registerDevice(device);

  const purchase = await webhookPost(
    { event: { type: 'INITIAL_PURCHASE', app_user_id: device } },
    signedWebhook({ type: 'INITIAL_PURCHASE', app_user_id: device }),
  );
  assert.equal(purchase.status, 200);
  assert.equal(purchase.json.isPro, true);

  const me = await get('/api/v1/auth/me', reg.json.token);
  assert.equal(me.json.isPro, true);

  const expirationBody = { event: { type: 'EXPIRATION', app_user_id: device } };
  const expiration = await webhookPost(expirationBody, signedWebhook(expirationBody.event));
  assert.equal(expiration.json.isPro, false);
});

test('webhook ignores events for unknown users but stays authorized', async () => {
  const event = { type: 'INITIAL_PURCHASE', app_user_id: 'unknown-device-xyz' };
  const res = await webhookPost({ event }, signedWebhook(event));
  assert.equal(res.status, 200);
  assert.equal(res.json.matched, false);
});

test('calendar event and list item fall back to simulated storage when integrations are unconfigured', async () => {
  const { json } = await registerDevice(randomUUID());
  const token = json.token;

  const calAction = {
    id: `cal-sim-${randomUUID()}`,
    type: 'calendar_event',
    title: 'Dentist Checkup',
    start: '2026-09-10T14:00:00Z',
    attendees: ['dr.smith@example.com'],
  };
  const listAction = {
    id: `list-sim-${randomUUID()}`,
    type: 'list_item',
    text: 'Almond Milk',
    list: 'groceries',
  };

  const execRes = await post('/api/v1/actions/execute', {
    actions: [calAction, listAction],
  }, token);

  assert.equal(execRes.status, 200);
  assert.equal(execRes.json.results.length, 2);

  const calResult = execRes.json.results[0];
  assert.equal(calResult.actionId, calAction.id);
  assert.equal(calResult.success, true);
  assert.equal(calResult.destination, 'google_calendar_simulated');
  assert.equal(calResult.executionTarget, 'storage');
  assert.equal(calResult.simulated, true);

  const listResult = execRes.json.results[1];
  assert.equal(listResult.actionId, listAction.id);
  assert.equal(listResult.success, true);
  assert.equal(listResult.destination, 'notion_simulated');
  assert.equal(listResult.executionTarget, 'storage');
  assert.equal(listResult.simulated, true);

  const history = await get('/api/v1/actions', token);
  assert.equal(history.status, 200);
  const foundCal = history.json.actions.find((a) => a.id === calAction.id);
  assert.ok(foundCal);
  assert.equal(foundCal.destination, 'google_calendar_simulated');
  assert.deepEqual(foundCal.attendees, ['dr.smith@example.com']);

  const listsRes = await get('/api/v1/actions/lists', token);
  assert.equal(listsRes.status, 200);
  const groceryList = listsRes.json.lists.find((l) => l.name === 'groceries');
  assert.ok(groceryList);
  assert.ok(groceryList.items.some((it) => it.id === listAction.id && it.text === 'Almond Milk'));

  const doneRes = await post(`/api/v1/actions/${listAction.id}/done`, { done: true }, token);
  assert.equal(doneRes.status, 200);
  assert.equal(doneRes.json.done, true);
});

test('calendar event and list item execute in live mode when configured, and fall back on API error', async () => {
  let mockShouldFail = false;
  let receivedGcalBody = null;
  let receivedNotionBody = null;
  let receivedDonePatch = false;

  const mockServer = http.createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) {
      body += chunk;
    }

    if (mockShouldFail) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Simulated service failure' }));
      return;
    }

    if (req.url.includes('/calendar/v3/calendars')) {
      receivedGcalBody = JSON.parse(body || '{}');
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ id: 'gcal_live_event_999', status: 'confirmed' }));
      return;
    }

    if (req.url.includes('/pages') && req.method === 'POST') {
      receivedNotionBody = JSON.parse(body || '{}');
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ id: 'notion_live_page_888', object: 'page' }));
      return;
    }

    if (req.url.includes('/pages/notion_live_page_888') && req.method === 'PATCH') {
      receivedDonePatch = true;
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ id: 'notion_live_page_888', properties: { Done: { checkbox: true } } }));
      return;
    }

    res.writeHead(404);
    res.end();
  });

  await new Promise((resolve) => mockServer.listen(0, '127.0.0.1', resolve));
  const mockPort = mockServer.address().port;
  const mockBaseUrl = `http://127.0.0.1:${mockPort}`;

  process.env.GOOGLE_CALENDAR_API_BASE = mockBaseUrl;
  process.env.GOOGLE_CALENDAR_TOKEN = 'mock-gcal-token';
  process.env.NOTION_API_BASE = mockBaseUrl;
  process.env.NOTION_INTEGRATION_TOKEN = 'mock-notion-token';
  process.env.NOTION_DATABASES = JSON.stringify({ general: 'db-gen-1', groceries: 'db-groc-1' });

  try {
    const { json } = await registerDevice(randomUUID());
    const token = json.token;

    const calAction = {
      id: `cal-live-${randomUUID()}`,
      type: 'calendar_event',
      title: 'Strategy Meeting',
      start: '2026-09-12T15:00:00Z',
    };
    const listAction = {
      id: `list-live-${randomUUID()}`,
      type: 'list_item',
      text: 'Fresh Basil',
      list: 'groceries',
    };

    const liveRes = await post('/api/v1/actions/execute', {
      actions: [calAction, listAction],
    }, token);

    assert.equal(liveRes.status, 200);
    const liveCal = liveRes.json.results[0];
    assert.equal(liveCal.actionId, calAction.id);
    assert.equal(liveCal.destination, 'google_calendar');
    assert.equal(liveCal.executionTarget, 'google_calendar');
    assert.equal(liveCal.success, true);
    assert.equal(receivedGcalBody?.summary, 'Strategy Meeting');

    const liveList = liveRes.json.results[1];
    assert.equal(liveList.actionId, listAction.id);
    assert.equal(liveList.destination, 'notion');
    assert.equal(liveList.executionTarget, 'notion');
    assert.equal(liveList.success, true);
    assert.equal(liveList.notionPageId, 'notion_live_page_888');
    assert.equal(receivedNotionBody?.properties?.title?.title?.[0]?.text?.content, 'Fresh Basil');

    const doneRes = await post(`/api/v1/actions/${listAction.id}/done`, { done: true }, token);
    assert.equal(doneRes.status, 200);
    assert.equal(receivedDonePatch, true);

    mockShouldFail = true;
    const { json: fallbackUser } = await registerDevice(randomUUID());
    const failCalAction = {
      id: `cal-fail-${randomUUID()}`,
      type: 'calendar_event',
      title: 'Coffee Sync',
      start: '2026-09-13T09:00:00Z',
    };
    const failListAction = {
      id: `list-fail-${randomUUID()}`,
      type: 'list_item',
      text: 'Espresso Beans',
      list: 'groceries',
    };

    const fallbackRes = await post('/api/v1/actions/execute', {
      actions: [failCalAction, failListAction],
    }, fallbackUser.token);

    assert.equal(fallbackRes.status, 200);
    const fallbackCal = fallbackRes.json.results[0];
    assert.equal(fallbackCal.destination, 'google_calendar_simulated');
    assert.equal(fallbackCal.executionTarget, 'storage');
    assert.equal(fallbackCal.success, true);

    const fallbackList = fallbackRes.json.results[1];
    assert.equal(fallbackList.destination, 'notion_simulated');
    assert.equal(fallbackList.executionTarget, 'storage');
    assert.equal(fallbackList.success, true);
  } finally {
    delete process.env.GOOGLE_CALENDAR_API_BASE;
    delete process.env.GOOGLE_CALENDAR_TOKEN;
    delete process.env.NOTION_API_BASE;
    delete process.env.NOTION_INTEGRATION_TOKEN;
    delete process.env.NOTION_DATABASES;
    await new Promise((resolve) => mockServer.close(resolve));
  }
});

test('POST /api/v1/actions/parse validates transcript, forwards nowIso and timeZone, and falls back safely', async () => { // marker
  const { json: user } = await registerDevice(randomUUID());

  const emptyRes = await post('/api/v1/actions/parse', {}, user.token);
  assert.equal(emptyRes.status, 400);
  assert.match(emptyRes.json.error, /transcript is required/i);

  const blankRes = await post('/api/v1/actions/parse', { transcript: '   ' }, user.token);
  assert.equal(blankRes.status, 400);
  assert.match(blankRes.json.error, /transcript is required/i);

  const unconfiguredBare = await post('/api/v1/actions/parse', { transcript: 'Buy milk' }, user.token);
  assert.equal(unconfiguredBare.status, 503);
  assert.equal(unconfiguredBare.json.code, 'llm_not_configured');

  let receivedOpenRouterPayload = null;
  const mockOpenRouter = http.createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) {
      body += chunk;
    }
    receivedOpenRouterPayload = JSON.parse(body || '{}');

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      choices: [
        {
          message: {
            tool_calls: [
              {
                function: {
                  name: 'parse_voice_intents',
                  arguments: JSON.stringify({
                    intents: [
                      {
                        type: 'calendar_event',
                        title: 'Doctor appointment',
                        date: '2026-09-10',
                        time: '10:00',
                      },
                    ],
                  }),
                },
              },
            ],
          },
        },
      ],
    }));
  });

  await new Promise((resolve) => mockOpenRouter.listen(0, '127.0.0.1', resolve));
  const openRouterPort = mockOpenRouter.address().port;

  process.env.OPENROUTER_BASE_URL = `http://127.0.0.1:${openRouterPort}`;
  process.env.OPENROUTER_API_KEY = 'test-openrouter-key';

  try {
    const customPayloadRes = await post('/api/v1/actions/parse', {
      transcript: 'Doctor appointment tomorrow at 10 AM',
      nowIso: '2026-09-09T14:00:00.000Z',
      timeZone: 'America/New_York',
    }, user.token);

    assert.equal(customPayloadRes.status, 200);
    assert.equal(customPayloadRes.json.source, 'llm');
    assert.equal(customPayloadRes.json.actions.length, 1);
    assert.equal(customPayloadRes.json.actions[0].title, 'Doctor appointment');

    const userMessage = receivedOpenRouterPayload?.messages?.find((m) => m.role === 'user')?.content || '';
    assert.match(userMessage, /2026-09-09T14:00:00.000Z/);
    assert.match(userMessage, /Doctor appointment tomorrow at 10 AM/);

    const barePayloadRes = await post('/api/v1/actions/parse', {
      transcript: 'Doctor appointment tomorrow at 10 AM',
    }, user.token);

    assert.equal(barePayloadRes.status, 200);
    assert.equal(barePayloadRes.json.source, 'llm');
    assert.equal(barePayloadRes.json.actions.length, 1);
  } finally {
    delete process.env.OPENROUTER_BASE_URL;
    delete process.env.OPENROUTER_API_KEY;
    await new Promise((resolve) => mockOpenRouter.close(resolve));
  }
});

// --- Integrations (mail & WhatsApp connections) ------------------------------

test('integrations require authentication and reject unknown channels', async () => {
  assert.equal((await get('/api/v1/integrations')).status, 401);
  const { json } = await registerDevice(randomUUID());
  const del = await fetch(`${base}/api/v1/integrations/bogus`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${json.token}` },
  });
  assert.equal(del.status, 400);
});

test('email connection validates input and rejects unreachable SMTP before storing anything', async () => {
  const { json } = await registerDevice(randomUUID());
  const token = json.token;

  const badHost = await post('/api/v1/integrations/email', { host: 'nope', user: 'a@b.dev', password: 'Password123!' }, token);
  assert.equal(badHost.status, 400);

  const badUser = await post('/api/v1/integrations/email', { host: 'smtp.gmail.com', user: 'not-an-email', password: 'Password123!' }, token);
  assert.equal(badUser.status, 400);

  const shortPw = await post('/api/v1/integrations/email', { host: 'smtp.gmail.com', user: 'a@b.dev', password: 'short' }, token);
  assert.equal(shortPw.status, 400);

  // Unreachable host: verify fails, nothing stored.
  const unreachable = await post('/api/v1/integrations/email', {
    host: '127.0.0.1', port: 1, user: 'a@b.dev', password: 'Password123!',
  }, token);
  assert.equal(unreachable.status, 400);
  assert.equal(unreachable.json.code, 'smtp_verify_failed');

  const status = await get('/api/v1/integrations', token);
  assert.equal(status.json.email.connected, false);
});

test('whatsapp connection validates input and rejects bad tokens before storing anything', async () => {
  const { json } = await registerDevice(randomUUID());
  const token = json.token;

  const badId = await post('/api/v1/integrations/whatsapp', { phoneNumberId: 'abc!', accessToken: 'x'.repeat(30) }, token);
  assert.equal(badId.status, 400);

  const shortToken = await post('/api/v1/integrations/whatsapp', { phoneNumberId: '123456789', accessToken: 'short' }, token);
  assert.equal(shortToken.status, 400);

  // Graph call against a bogus ID with a bogus token must fail.
  const badVerify = await post('/api/v1/integrations/whatsapp', {
    phoneNumberId: '123456789', accessToken: 'x'.repeat(40),
  }, token);
  assert.equal(badVerify.status, 400);
  assert.equal(badVerify.json.code, 'whatsapp_verify_failed');
});

test('cancel-message without any connected channel reports not delivered and still logs the audit record', async () => {
  const { json } = await registerDevice(randomUUID());
  const token = json.token;

  const calAction = {
    id: `cal-cancel-${randomUUID()}`,
    type: 'calendar_event',
    title: 'Meeting with Josephine',
    start: '2026-09-25T15:00:00Z',
    attendees: ['josephine@example.com'],
  };
  await post('/api/v1/actions/execute', { actions: [calAction] }, token);

  const send = await post(`/api/v1/calendar/${calAction.id}/cancel-message`, {
    email: 'josephine@example.com',
    message: 'Hi Josephine, I need to cancel our meeting.',
  }, token);
  assert.equal(send.status, 200);
  assert.equal(send.json.delivered, false);
  assert.ok(send.json.outcomes.length >= 1);

  const sendBadEmail = await post(`/api/v1/calendar/${calAction.id}/cancel-message`, {
    email: 'not-an-email', message: 'x',
  }, token);
  assert.equal(sendBadEmail.status, 400);
});

test('delete removes the event and reports google sync attempt state', async () => {
  const { json } = await registerDevice(randomUUID());
  const token = json.token;

  const calAction = {
    id: `cal-del-${randomUUID()}`,
    type: 'calendar_event',
    title: 'To be cancelled',
    start: '2026-09-26T10:00:00Z',
  };
  await post('/api/v1/actions/execute', { actions: [calAction] }, token);

  // Simulated (unconfigured Google) -> no google id recorded -> attempted=false
  const del = await post(`/api/v1/calendar/${calAction.id}/delete`, undefined, token);
  assert.equal(del.status, 200);
  assert.equal(del.json.ok, true);
  assert.equal(del.json.googleAttempted, false);

  const gone = await post(`/api/v1/calendar/${calAction.id}/delete`, undefined, token);
  assert.equal(gone.status, 404);
});
