import { Router } from 'express';
import { authenticate, consumeQuota, quotaRemaining, publicUser } from '../auth.js';
import { parseActions } from '../llm.js';
import {
  appendItem as appendStorageItem,
  setItemDone as setStorageItemDone,
  createCalendarEvent as createStorageCalendarEvent,
} from '../integrations/storage.js';
import { createCalendarEvent as createGoogleCalendarEvent } from '../integrations/google-calendar.js';
import { appendNotionItem, setNotionDone } from '../integrations/notion.js';
import { config, isGoogleCalendarConfigured, isNotionConfigured } from '../config.js';
import { db } from '../db.js';

const router = Router();

router.use(authenticate);

function badRequest(res, message) {
  return res.status(400).json({ error: message });
}

const actionsCollection = (user) => `actions.${user.id}`;

export function getExistingListNames(user) {
  if (!user?.id) return [];
  const items = db.all(actionsCollection(user));
  const names = new Set();
  for (const item of items) {
    if (item.type === 'list_item' || item.type === 'list') {
      const name = item.list || item.list_name;
      if (name) names.add(String(name).trim());
    }
  }
  return Array.from(names);
}

async function executeCalendarAction(action, id, user) {
  let outcome = null;

  if (isGoogleCalendarConfigured()) {
    try {
      const liveOutcome = await createGoogleCalendarEvent(action);
      if (liveOutcome && liveOutcome.success) {
        outcome = {
          actionId: id,
          destination: 'google_calendar',
          executionTarget: 'google_calendar',
          success: true,
          eventId: liveOutcome.eventId || null,
          message: liveOutcome.message || `"${action.title || 'New event'}" added to your Google calendar`,
        };
        await createStorageCalendarEvent(action).catch(() => {});
      }
    } catch {
      outcome = null;
    }
  }

  if (!outcome) {
    try {
      const storageOutcome = await createStorageCalendarEvent(action);
      outcome = {
        actionId: id,
        destination: 'google_calendar_simulated',
        executionTarget: 'storage',
        simulated: true,
        success: Boolean(storageOutcome?.success),
        event: storageOutcome?.event,
        message: `"${action.title || 'New event'}" saved locally (Google Calendar simulated)`,
      };
    } catch (err) {
      outcome = {
        actionId: id,
        destination: 'google_calendar_simulated',
        executionTarget: 'storage',
        simulated: true,
        success: false,
        message: `Failed to save calendar event: ${err.message}`,
      };
    }
  }

  persistAction(user, {
    id,
    type: 'calendar_event',
    title: action.title || 'New event',
    at: action.start || null,
    attendees: action.attendees || [],
    destination: outcome.destination,
    executionTarget: outcome.executionTarget,
    eventId: outcome.eventId || null,
    google_event_id: outcome.eventId || null,
    success: outcome.success,
    message: outcome.message,
  });

  return outcome;
}

async function executeListItemAction(action, id, user) {
  let outcome = null;

  if (isNotionConfigured()) {
    try {
      const liveOutcome = await appendNotionItem(action);
      if (liveOutcome && liveOutcome.success) {
        const storageOutcome = await appendStorageItem(action).catch(() => null);
        outcome = {
          actionId: id,
          destination: 'notion',
          executionTarget: 'notion',
          notionPageId: liveOutcome.notionPageId || null,
          storageItemId: storageOutcome?.item?.id || null,
          success: true,
          message: liveOutcome.message || `"${action.text}" added to Notion (${action.list || 'general'})`,
        };
      }
    } catch {
      outcome = null;
    }
  }

  if (!outcome) {
    try {
      const storageOutcome = await appendStorageItem(action);
      outcome = {
        actionId: id,
        destination: 'notion_simulated',
        executionTarget: 'storage',
        simulated: true,
        success: Boolean(storageOutcome?.success),
        item: storageOutcome?.item,
        storageItemId: storageOutcome?.item?.id || null,
        message: `"${action.text}" saved to ${action.list || 'general'} list (Notion simulated)`,
      };
    } catch (err) {
      outcome = {
        actionId: id,
        destination: 'notion_simulated',
        executionTarget: 'storage',
        simulated: true,
        success: false,
        message: `Failed to save list item: ${err.message}`,
      };
    }
  }

  persistAction(user, {
    id,
    type: 'list_item',
    text: action.text || '',
    list: String(action.list || 'general').toLowerCase(),
    priority: action.priority || null,
    destination: outcome.destination,
    executionTarget: outcome.executionTarget,
    notionPageId: outcome.notionPageId || null,
    storageItemId: outcome.storageItemId || null,
    success: outcome.success,
    message: outcome.message,
  });

  return outcome;
}

function executeReminderAction(action, id, user) {
  const stored = {
    id,
    title: action.title || 'Reminder',
    dueAt: action.due_at || null,
    priority: action.priority || null,
    createdAt: Date.now(),
  };
  db.set(`reminders.${user.id}`, id, stored);
  persistAction(user, {
    id,
    type: 'reminder',
    title: stored.title,
    at: stored.dueAt,
    destination: 'reminders',
    executionTarget: 'reminders',
    success: true,
    message: `Reminder set: "${stored.title}"`,
  });
  return {
    actionId: id,
    destination: 'reminders',
    executionTarget: 'reminders',
    success: true,
    scheduleLocal: true,
    message: `Reminder set: "${stored.title}"`,
  };
}

function executeTaskAction(action, id, user) {
  const stored = {
    id,
    title: action.title || 'Task',
    dueAt: action.due_date ? `${action.due_date}T09:00:00Z` : (action.due_at || null),
    priority: action.priority || null,
    project: action.project || null,
    createdAt: Date.now(),
  };
  db.set(`reminders.${user.id}`, id, stored);
  persistAction(user, {
    id,
    type: 'reminder',
    title: stored.title,
    at: stored.dueAt,
    priority: stored.priority,
    project: stored.project,
    destination: 'reminders',
    executionTarget: 'reminders',
    success: true,
    message: `Task scheduled: "${stored.title}"`,
  });
  return {
    actionId: id,
    destination: 'reminders',
    executionTarget: 'reminders',
    success: true,
    scheduleLocal: true,
    message: `Task scheduled: "${stored.title}"`,
  };
}

async function executeListAction(action, id, user) {
  const listName = String(action.list_name || action.list || 'general').trim();
  const items = Array.isArray(action.items) ? action.items : (action.text ? [action.text] : ['Item']);
  const isNewList = Boolean(action.is_new_list);
  const itemOutcomes = [];

  for (let i = 0; i < items.length; i++) {
    const itemText = items[i];
    const itemAction = {
      id: `${id}_${i}`,
      type: 'list_item',
      text: itemText,
      list: listName.toLowerCase(),
      list_name: listName,
      is_new_list: isNewList,
    };

    const outcome = await executeListItemAction(itemAction, itemAction.id, user);
    itemOutcomes.push(outcome);
  }

  return {
    actionId: id,
    destination: itemOutcomes[0]?.destination || 'notion_simulated',
    executionTarget: itemOutcomes[0]?.executionTarget || 'storage',
    success: itemOutcomes.every((o) => o.success),
    is_new_list: isNewList,
    list_name: listName,
    itemCount: items.length,
    message: `List "${listName}": added ${items.length} item(s)`,
    items: itemOutcomes,
  };
}

function executeNoteAction(action, id, user) {
  const content = String(action.content || action.text || '').trim();
  persistAction(user, {
    id,
    type: 'note',
    text: content,
    destination: action.destination || 'storage',
    executionTarget: 'storage',
    success: true,
    message: `Note saved: "${content.slice(0, 30)}"`,
  });
  return {
    actionId: id,
    destination: 'storage',
    executionTarget: 'storage',
    success: true,
    message: 'Note saved',
  };
}

function persistAction(user, entry) {
  const collection = actionsCollection(user);
  const existing = db.get(collection, entry.id);
  db.set(collection, entry.id, {
    done: false,
    createdAt: existing?.createdAt ?? Date.now(),
    ...entry,
  });
}

router.post('/parse', async (req, res, next) => {
  try {
    const { transcript, nowIso, timeZone } = req.body || {};
    const cleanTranscript = String(transcript || '').trim();
    if (!cleanTranscript) return badRequest(res, 'transcript is required');

    const effectiveNowIso = (typeof nowIso === 'string' && nowIso.trim()) ? nowIso.trim() : new Date().toISOString();
    const effectiveTimeZone = (typeof timeZone === 'string' && timeZone.trim()) ? timeZone.trim() : 'UTC';

    const existingLists = getExistingListNames(req.user);
    const parsed = await parseActions(cleanTranscript, {
      existingLists,
      nowIso: effectiveNowIso,
      timeZone: effectiveTimeZone,
    });
    res.json(parsed);
  } catch (err) {
    console.warn(`[actions.js:POST /parse] Parsing failed with code=${err.code}: ${err.message}`);
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
* Calendar events -> file storage
* List items     -> file storage
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
      let outcome = null;

      switch (action.type) {
        case 'calendar_event':
          outcome = await executeCalendarAction(action, id, req.user);
          break;
        case 'list_item':
          outcome = await executeListItemAction(action, id, req.user);
          break;
        case 'reminder':
          outcome = executeReminderAction(action, id, req.user);
          break;
        case 'task':
          outcome = executeTaskAction(action, id, req.user);
          break;
        case 'list':
          outcome = await executeListAction(action, id, req.user);
          break;
        case 'note':
          outcome = executeNoteAction(action, id, req.user);
          break;
        default:
          outcome = {
            actionId: id,
            destination: 'none',
            executionTarget: 'none',
            success: false,
            message: 'Unsupported action type',
          };
      }

      results.push(outcome);
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
    const key = item.list_name || item.list || 'general';
    (groups[key] = groups[key] || []).push(item);
  }
  const lists = Object.entries(groups).map(([name, listItems]) => ({
    name,
    items: listItems.map((it) => ({
      id: it.id,
      text: it.text,
      list: it.list || name.toLowerCase(),
      done: Boolean(it.done),
      createdAt: it.createdAt,
    })),
  }));
  res.json({ lists });
});

/**
 * POST /api/v1/actions/:id/done  { done: boolean }
 * Marks a list item done/undone. Syncs best-effort to storage.
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

    if (existing.type === 'list_item') {
      if (existing.notionPageId) {
        await setNotionDone(existing.notionPageId, done).catch(() => {});
      }
      await setStorageItemDone(existing.storageItemId || existing.id, done).catch(() => {});
    }
    res.json({ id, done });
  } catch (err) {
    next(err);
  }
});

export default router;

const MAX_ACTIONS_PER_REQUEST = 25;