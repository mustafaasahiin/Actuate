import { config } from '../config.js';
import fs from 'fs';
import path from 'node:path';

const STORAGE_DIR = path.resolve('data');
const ITEMS_FILE = path.join(STORAGE_DIR, 'items.json');
const CALENDAR_FILE = path.join(STORAGE_DIR, 'calendar-events.json');

function ensureStorageDir() {
  if (!fs.existsSync(STORAGE_DIR)) {
    fs.mkdirSync(STORAGE_DIR, { recursive: true });
  }
}

function readItems() {
  ensureStorageDir();
  const data = fs.readFileSync(ITEMS_FILE, 'utf-8');
  return data ? JSON.parse(data) : [];
}

function writeItems(items) {
  ensureStorageDir();
  fs.writeFileSync(ITEMS_FILE, JSON.stringify(items, null, 2));
}

function readCalendarEvents() {
  ensureStorageDir();
  const data = fs.readFileSync(CALENDAR_FILE, 'utf-8');
  return data ? JSON.parse(data) : [];
}

function writeCalendarEvents(events) {
  ensureStorageDir();
  fs.writeFileSync(CALENDAR_FILE, JSON.stringify(events, null, 2));
}

/**
 * Appends a list item to the JSON storage file.
 * Returns the created item with an ID.
 */
export async function appendItem({ text, list }) {
  const items = readItems();
  const id = `item_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;
  const item = {
    id,
    text,
    done: false,
    list: list || 'general',
    createdAt: Date.now(),
  };
  items.push(item);
  writeItems(items);
  return { success: true, destination: 'storage', item };
}

/**
 * Sets the "done" status of an item in the JSON storage file.
 */
export async function setItemDone(itemId, done) {
  const items = readItems();
  const item = items.find((i) => i.id === itemId);
  if (!item) {
    return { success: false, message: `Item "${itemId}" not found` };
  }
  item.done = done;
  writeItems(items);
  return { success: true };
}

/**
 * Creates a calendar event in the JSON storage file.
 */
export async function createCalendarEvent(action) {
  const events = readCalendarEvents();
  const id = `event_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;
  const event = {
    id,
    title: action.title || 'New event',
    due_at: action.start || null,
    all_day: action.all_day || false,
    location: action.location || '',
    description: action.description || '',
    attendees: action.attendees || [],
    createdAt: Date.now(),
  };
  events.push(event);
  writeCalendarEvents(events);
  return { success: true, destination: 'storage', event };
}

/**
 * Gets all stored list items grouped by list name.
 */
export function getItemsByList() {
  const items = readItems();
  const groups = {};
  for (const item of items) {
    const key = item.list || 'general';
    (groups[key] = groups[key] || []).push(item);
  }
  return Object.entries(groups).map(([name, listItems]) => ({
    name,
    items: listItems.map((it) => ({
      id: it.id,
      text: it.text,
      list: it.list || 'general',
      done: Boolean(it.done),
      createdAt: it.createdAt,
    })),
  }));
}

/**
 * Gets all stored calendar events.
 */
export function getCalendarEvents() {
  return readCalendarEvents();
}