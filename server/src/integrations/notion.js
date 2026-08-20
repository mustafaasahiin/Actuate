import { config } from '../config.js';

/**
 * Appends a page (list item) to a Notion database via the REST API.
 */
export async function appendNotionItem({ text, list }) {
  if (!config.notion.enabled) {
    return { success: false, destination: 'notion', message: 'Notion not configured on the server' };
  }
  const key = String(list || 'general').toLowerCase();
  const databaseId = config.notion.databases[key] || config.notion.databases.general;
  if (!databaseId) {
    return {
      success: false,
      destination: 'notion',
      message: `No Notion database mapped for "${list || 'general'}"`,
    };
  }

  const body = {
    parent: { database_id: databaseId },
    properties: {
      title: { title: [{ text: { content: text } }] },
    },
  };

  const res = await fetch(`${config.notion.apiBase}/pages`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${config.notion.token}`,
      'Notion-Version': '2022-06-28',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const code = res.status;
    const message =
      code === 401 ? 'Notion auth failed — check the integration token'
        : code === 403 ? 'Notion access denied — invite the integration to the database'
          : code === 404 ? 'Notion database not found — check the database id'
            : code === 429 ? 'Notion rate limit reached — try again shortly'
              : `Notion request failed: HTTP ${code}`;
    return { success: false, destination: 'notion', message };
  }

  const data = await res.json();
  return {
    success: true,
    destination: 'notion',
    notionPageId: data.id || null,
    message: `"${text}" added to Notion (${key})`,
  };
}

/**
 * Best-effort check-off sync: sets the "Done" checkbox on a Notion page.
 * Returns success:false if the database has no "Done" property — the app
 * state stays authoritative either way.
 */
export async function setNotionDone(pageId, done) {
  if (!config.notion.enabled) {
    return { success: false, message: 'Notion not configured on the server' };
  }
  const res = await fetch(`${config.notion.apiBase}/pages/${pageId}`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${config.notion.token}`,
      'Notion-Version': '2022-06-28',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ properties: { Done: { checkbox: done } } }),
  });
  if (!res.ok) {
    return {
      success: false,
      message: `Notion check-off failed: HTTP ${res.status} (database needs a "Done" checkbox property)`,
    };
  }
  return { success: true };
}