import { Router } from 'express';
import { authenticate, consumeQuota, quotaRemaining, publicUser } from '../auth.js';
import { parseActions } from '../llm.js';
import { appendNotionItem, setNotionDone } from '../integrations/notion.js';
import { createCalendarEvent } from '../integrations/google-calendar.js';
import { config } from '../config.js';
import { db } from '../db.js';

const router = Router();

router.use(authenticate);

function badRequest(res, message) {
  return res.status(400).json({ error: message });
}

const actionsCollection = (user) => `actions.${user.id}`;

/**
 * Persists one executed action so the app can render sections (Today,
 * Lists) and history across devices. Cheap JSON store; swap for a real DB
 * before production.
 */
function persistAction(user, entry) {
  const collection = actionsCollection(user);
  const existing = db.get(collection, entry.id);
  db.set(collection, entry.id, {
    done: false,
    createdAt: existing?.createdAt ?? Date.now(),
    ...entry,
  });
}

/**
 * POST /api/v1/actions/parse
 * { transcript } -> { actions, source, confidence }
 */
router.post('/parse', async (req, res, next) => {
  try {
    const transcript = String(req.body?.transcript || '').trim();
    if (!transcript) return badRequest(res, 'transcript is required');
    const parsed = await parseActions(transcript);
    res.json(parsed);
  } catch (err) {
    if (err.code === 'llm_not_configured') {
      return res.status(503).json({ error: err.message, code: err.code });
    }
    next(err);
  }
});

/**
 * POST /api/v1/actions/execute
 * { actions: [{ id, type, ... }] } -> { results, quotaRemaining }
 *
 * Calendar events -> Google Calendar API
 * List items     -> Notion API
 * Reminders      -> returned to the app to schedule locally (EventKit's
 *                   Android equivalent: AlarmManager). Also persisted server-side.
 */
router.post('/execute', async (req, res, next) => {
  try {
    const actions = Array.isArray(req.body?.actions) ? req.body.actions : [];
    if (!actions.length) return badRequest(res, 'actions array is required');
    if (actions.length > MAX_ACTIONS_PER_REQUEST) {
      return badRequest(res, `Too many actions in one request (max ${MAX_ACTIONS_PER_REQUEST})`);
    }

    const consumed = consumeQuota(req.user, actions.length);
    if (!consumed.ok) {
      return res.status(429).json({
        error: `Free tier: ${config.freeQuotaPerWeek} actions per week. Upgrade for unlimited.`,
        code: 'quota_exceeded',
        quotaRemaining: quotaRemaining(req.user),
      });
    }

    const results = [];
    for (const action of actions) {
      const id = String(action.id || 'unknown');
      switch (action.type) {
        case 'calendar_event': {
          const outcome = await createCalendarEvent(action);
          persistAction(req.user, {
            id,
            type: 'calendar_event',
            title: action.title || 'New event',
            at: action.start || null,
            destination: outcome.success ? 'calendar' : 'none',
            success: outcome.success,
            message: outcome.message,
          });
          results.push({ ...outcome, actionId: id });
          break;
        }
        case 'list_item': {
          const outcome = await appendNotionItem(action);
          persistAction(req.user, {
            id,
            type: 'list_item',
            text: action.text || '',
            list: String(action.list || 'general').toLowerCase(),
            priority: action.priority || null,
            notionPageId: outcome.notionPageId || null,
            destination: 'notion',
            success: outcome.success,
            message: outcome.message,
          });
          results.push({
            actionId: id,
            destination: 'notion',
            success: outcome.success,
            message: outcome.message,
          });
          break;
        }
        case 'reminder': {
          const stored = {
            id,
            title: action.title || 'Reminder',
            dueAt: action.due_at || null,
            priority: action.priority || null,
            createdAt: Date.now(),
          };
          db.set(`reminders.${req.user.id}`, id, stored);
          persistAction(req.user, {
            id,
            type: 'reminder',
            title: stored.title,
            at: stored.dueAt,
            destination: 'reminders',
            success: true,
            message: `Reminder set: "${stored.title}"`,
          });
          results.push({
            actionId: id,
            destination: 'reminders',
            success: true,
            scheduleLocal: true,
            message: `Reminder set: "${stored.title}"`,
          });
          break;
        }
        default: {
          results.push({
            actionId: id,
            destination: 'none',
            success: false,
            message: 'Unsupported action type',
          });
        }
      }
    }

    res.json({ results, quotaRemaining: quotaRemaining(req.user) });
  } catch (err) {
    next(err);
  }
});

/**
 * GET /api/v1/actions?limit=50  -> full executed-action history for this user
 * (calendar events, list items and reminders), newest first.
 */
router.get('/', (req, res) => {
  const limit = Math.min(Number(req.query?.limit) || 50, 500);
  const items = db.all(actionsCollection(req.user))
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, limit);
  res.json({ actions: items });
});

/**
 * GET /api/v1/actions/lists  -> list items grouped by list name, for the
 * Lists section of the app.
 */
router.get('/lists', (req, res) => {
  const items = db.all(actionsCollection(req.user))
    .filter((a) => a.type === 'list_item')
    .sort((a, b) => a.done - b.done || b.createdAt - a.createdAt);
  const groups = {};
  for (const item of items) {
    const key = item.list || 'general';
    (groups[key] = groups[key] || []).push(item);
  }
  const lists = Object.entries(groups).map(([name, listItems]) => ({
    name,
    items: listItems.map((it) => ({
      id: it.id,
      text: it.text,
      list: it.list || 'general',
      done: Boolean(it.done),
      createdAt: it.createdAt,
    })),
  }));
  res.json({ lists });
});

/**
 * POST /api/v1/actions/:id/done  { done: boolean }
 * Marks a list item done/undone. Syncs best-effort to Notion (Done checkbox).
 */
router.post('/:id/done', async (req, res, next) => {
  try {
    const { id } = req.params;
    const done = Boolean(req.body?.done);
    const existing = db.get(actionsCollection(req.user), id);
    if (!existing) {
      return res.status(404).json({ error: 'Action not found' });
    }
    db.update(actionsCollection(req.user), id, { done });

    if (existing.type === 'list_item' && existing.notionPageId) {
      await setNotionDone(existing.notionPageId, done).catch(() => {});
    }
    res.json({ id, done });
  } catch (err) {
    next(err);
  }
});

export default router;

const MAX_ACTIONS_PER_REQUEST = 25;