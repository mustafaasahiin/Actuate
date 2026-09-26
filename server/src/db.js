import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

let memory = null;
let filePath = null;

/**
 * Tiny JSON-file store for the dev server. Each key maps to an object.
 * Writes are synchronous and atomic: write a temp file, then rename it over
 * the real file so a crash mid-write can never corrupt the store.
 */
export function initDb(path) {
  filePath = path;
  mkdirSync(dirname(filePath), { recursive: true });
  memory = { users: {}, actions: {}, reminders: {} };
  try {
    const raw = readFileSync(filePath, 'utf8');
    memory = { ...memory, ...JSON.parse(raw) };
  } catch {
    persist();
  }
}

function persist() {
  const tmp = `${filePath}.tmp`;
  writeFileSync(tmp, JSON.stringify(memory, null, 2));
  renameSync(tmp, filePath);
}

export const db = {
  get(collection, id) {
    return memory[collection]?.[id] ?? null;
  },
  set(collection, id, value) {
    memory[collection] = memory[collection] || {};
    memory[collection][id] = value;
    persist();
  },
  update(collection, id, patch) {
    const existing = db.get(collection, id);
    const next = { ...(existing || {}), ...patch };
    db.set(collection, id, next);
    return next;
  },
  all(collection) {
    return Object.values(memory[collection] || {});
  },
  remove(collection, id) {
    if (memory[collection] && id in memory[collection]) {
      delete memory[collection][id];
      persist();
    }
  },
};