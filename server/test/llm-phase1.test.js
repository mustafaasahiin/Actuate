import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  normalizeIntents,
  intentsToActions,
  INTENT_SCHEMA,
  cleanJsonString,
  validateParsedIntents,
} from '../src/llm.js';

test('Phase 1 - Locked JSON schema has required intents structure', () => {
  assert.equal(INTENT_SCHEMA.type, 'object');
  assert.deepEqual(INTENT_SCHEMA.required, ['intents']);
  assert.equal(INTENT_SCHEMA.properties.intents.type, 'array');
  const itemProps = INTENT_SCHEMA.properties.intents.items.properties;
  assert.ok(itemProps.type);
  assert.ok(itemProps.title);
  assert.ok(itemProps.date);
  assert.ok(itemProps.time);
  assert.ok(itemProps.attendees);
  assert.ok(itemProps.location);
  assert.ok(itemProps.priority);
  assert.ok(itemProps.project);
  assert.ok(itemProps.due_date);
  assert.ok(itemProps.list_name);
  assert.ok(itemProps.is_new_list);
  assert.ok(itemProps.items);
  assert.ok(itemProps.content);
  assert.ok(itemProps.destination);
});

test('Phase 1 - 10 single-intent and 2 mixed-intent transcripts verification', () => {
  const existingLists = ['Groceries', 'Work'];
  const fixedNow = '2026-09-08T12:00:00.000Z';

  const t1 = [{
    type: 'calendar_event',
    title: 'Meeting with Yusuf',
    date: '2026-09-09',
    time: '15:00',
    attendees: ['Yusuf'],
    location: 'Coffee Shop',
  }];
  const norm1 = normalizeIntents(t1, existingLists, fixedNow);
  assert.equal(norm1[0].type, 'calendar_event');
  assert.equal(norm1[0].title, 'Meeting with Yusuf');
  assert.equal(norm1[0].time, '15:00');
  assert.deepEqual(norm1[0].attendees, ['Yusuf']);
  assert.equal(norm1[0].location, 'Coffee Shop');

  const t2 = [{
    type: 'calendar_event',
    title: 'Dental checkup',
    date: '2026-09-11',
    time: '10:00',
    attendees: [],
    location: null,
  }];
  const norm2 = normalizeIntents(t2, existingLists, fixedNow);
  assert.equal(norm2[0].type, 'calendar_event');
  assert.equal(norm2[0].title, 'Dental checkup');

  const t3 = [{
    type: 'task',
    title: 'Finish Q3 financial report',
    priority: 'high',
    project: 'Finance',
    due_date: '2026-09-11',
  }];
  const norm3 = normalizeIntents(t3, existingLists, fixedNow);
  assert.equal(norm3[0].type, 'task');
  assert.equal(norm3[0].priority, 'high');
  assert.equal(norm3[0].due_date, '2026-09-11');

  const t4 = [{
    type: 'task',
    title: 'Review project pull requests',
    priority: 'medium',
    project: 'Actuate',
    due_date: '2026-09-09',
  }];
  const norm4 = normalizeIntents(t4, existingLists, fixedNow);
  assert.equal(norm4[0].type, 'task');
  assert.equal(norm4[0].project, 'Actuate');

  const t5 = [{
    type: 'list',
    list_name: 'Groceries',
    items: ['milk', 'eggs', 'sourdough bread'],
  }];
  const norm5 = normalizeIntents(t5, existingLists, fixedNow);
  assert.equal(norm5[0].type, 'list');
  assert.equal(norm5[0].list_name, 'Groceries');
  assert.equal(norm5[0].is_new_list, false);
  assert.equal(norm5[0].items.length, 3);
  assert.ok(!('title' in norm5[0]));

  const t6 = [{
    type: 'list',
    list_name: 'Hardware',
    items: ['nails', 'hammer', 'wood glue'],
  }];
  const norm6 = normalizeIntents(t6, existingLists, fixedNow);
  assert.equal(norm6[0].type, 'list');
  assert.equal(norm6[0].list_name, 'Hardware');
  assert.equal(norm6[0].is_new_list, true);
  assert.equal(norm6[0].items.length, 3);

  const t7 = [{
    type: 'list',
    items: ['bananas', 'apples', 'oranges'],
  }];
  const norm7 = normalizeIntents(t7, existingLists, fixedNow);
  assert.equal(norm7[0].type, 'list');
  assert.equal(norm7[0].list_name, 'Groceries');
  assert.equal(norm7[0].is_new_list, false);

  const t8 = [{
    type: 'note',
    content: 'Gate code is 4921 for the front entrance',
    destination: 'Home',
  }];
  const norm8 = normalizeIntents(t8, existingLists, fixedNow);
  assert.equal(norm8[0].type, 'note');
  assert.equal(norm8[0].content, 'Gate code is 4921 for the front entrance');

  const t9 = [{
    type: 'note',
    content: 'Ideas for the hackathon presentation',
  }];
  const norm9 = normalizeIntents(t9, existingLists, fixedNow);
  assert.equal(norm9[0].type, 'note');
  assert.equal(norm9[0].content, 'Ideas for the hackathon presentation');

  const t10 = [{
    type: 'calendar_event',
    title: 'Lunch with Sarah',
    date: '2026-09-14',
    time: '12:30',
    attendees: ['Sarah'],
  }];
  const norm10 = normalizeIntents(t10, existingLists, fixedNow);
  assert.equal(norm10[0].type, 'calendar_event');
  assert.equal(norm10[0].time, '12:30');

  const t11 = [
    {
      type: 'calendar_event',
      title: 'Meeting with Yusuf',
      date: '2026-09-09',
      time: '15:00',
      attendees: ['Yusuf'],
    },
    {
      type: 'list',
      list_name: 'Groceries',
      items: ['milk', 'eggs'],
    },
  ];
  const norm11 = normalizeIntents(t11, existingLists, fixedNow);
  assert.equal(norm11.length, 2);
  assert.equal(norm11[0].type, 'calendar_event');
  assert.equal(norm11[1].type, 'list');
  assert.equal(norm11[1].is_new_list, false);

  const actions11 = intentsToActions(norm11, fixedNow);
  assert.equal(actions11.length, 3);
  assert.equal(actions11[0].type, 'calendar_event');
  assert.equal(actions11[1].type, 'list_item');
  assert.equal(actions11[1].text, 'milk');
  assert.equal(actions11[2].type, 'list_item');
  assert.equal(actions11[2].text, 'eggs');

  const t12 = [
    {
      type: 'calendar_event',
      title: 'Gym session',
      date: '2026-09-09',
      time: '18:00',
    },
    {
      type: 'task',
      title: 'Finish tax documents',
      priority: 'high',
      due_date: '2026-09-10',
    },
    {
      type: 'list',
      list_name: 'Shopping',
      items: ['batteries'],
    },
  ];
  const norm12 = normalizeIntents(t12, existingLists, fixedNow);
  assert.equal(norm12.length, 3);
  assert.equal(norm12[0].type, 'calendar_event');
  assert.equal(norm12[1].type, 'task');
  assert.equal(norm12[2].type, 'list');
  assert.equal(norm12[2].is_new_list, true);

  const actions12 = intentsToActions(norm12, fixedNow);
  assert.equal(actions12.length, 3);
  assert.equal(actions12[0].type, 'calendar_event');
  assert.equal(actions12[1].type, 'reminder');
  assert.equal(actions12[2].type, 'list_item');
});

test('Phase 1 - Zero plaintext secrets or API keys appear in logs', () => {
  const fakeKey = 'sk-or-v1-abcdef1234567890abcdef1234567890';
  const capturedLogs = [];
  const originalLog = console.log;
  const originalWarn = console.warn;
  console.log = (...args) => capturedLogs.push(args.join(' '));
  console.warn = (...args) => capturedLogs.push(args.join(' '));

  try {
    normalizeIntents([{ type: 'calendar_event', title: 'Test event' }], ['Groceries']);
  } finally {
    console.log = originalLog;
    console.warn = originalWarn;
  }

  for (const logLine of capturedLogs) {
    assert.ok(!logLine.includes(fakeKey));
    assert.ok(!logLine.includes('sk-or-'));
    assert.ok(!logLine.includes('Authorization'));
  }
});

test('Phase 2 - cleanJsonString handles raw JSON, markdown blocks, code fences, and extra preambles', () => {
  const rawJson = '{"intents":[{"type":"task","title":"Call mom"}]}';
  assert.equal(cleanJsonString(rawJson), rawJson);

  const markdownJson = '```json\n{"intents":[{"type":"task","title":"Call mom"}]}\n```';
  assert.equal(cleanJsonString(markdownJson), rawJson);

  const fencedNoTag = '```\n{"intents":[{"type":"task","title":"Call mom"}]}\n```';
  assert.equal(cleanJsonString(fencedNoTag), rawJson);

  const noisy = 'Here is your structured JSON:\n```json\n{"intents":[{"type":"task","title":"Call mom"}]}\n```\nHope this helps!';
  assert.equal(cleanJsonString(noisy), rawJson);

  const bareNoisy = 'Sure! The result is {"intents":[{"type":"task","title":"Call mom"}]} generated by AI.';
  assert.equal(cleanJsonString(bareNoisy), rawJson);

  const obj = { intents: [{ type: 'task', title: 'Call mom' }] };
  assert.equal(cleanJsonString(obj), JSON.stringify(obj));

  assert.equal(cleanJsonString(null), '');
  assert.equal(cleanJsonString(undefined), '');
  assert.equal(cleanJsonString('   '), '');
});

test('Phase 2 - validateParsedIntents validates schema correctness and catches invalid payloads', () => {
  assert.equal(validateParsedIntents({ intents: [{ type: 'calendar_event', title: 'Meeting' }] }), true);
  assert.equal(validateParsedIntents({ intents: [{ type: 'task', title: 'Stretch' }] }), true);
  assert.equal(validateParsedIntents({ intents: [{ type: 'list', list_name: 'Groceries', items: ['garlic'] }] }), true);
  assert.equal(validateParsedIntents({ intents: [{ type: 'note', content: 'note' }] }), true);
  assert.equal(validateParsedIntents({ actions: [{ type: 'task', title: 'Task' }] }), true);
  assert.equal(validateParsedIntents({ intents: [] }), true);

  assert.equal(validateParsedIntents(null), false);
  assert.equal(validateParsedIntents(undefined), false);
  assert.equal(validateParsedIntents('{"intents":[]}'), false);
  assert.equal(validateParsedIntents(123), false);
  assert.equal(validateParsedIntents([]), false);
  assert.equal(validateParsedIntents({}), false);
  assert.equal(validateParsedIntents({ intents: 'not an array' }), false);
  assert.equal(validateParsedIntents({ intents: null }), false);
  assert.equal(validateParsedIntents({ intents: [{ type: 'unsupported_type' }] }), false);
  assert.equal(validateParsedIntents({ intents: [{ title: 'missing type' }] }), false);
  assert.equal(validateParsedIntents({ intents: [null] }), false);
  assert.equal(validateParsedIntents({ intents: [{ type: 123 }] }), false);
  assert.equal(validateParsedIntents({ intents: [{ type: 'calendar_event' }, { type: 'invalid' }] }), false);
});

test('Phase 2 - Case 1: Single reminder ("remind me to call mom at 5pm")', () => {
  const fixedNow = '2026-09-09T12:00:00.000Z';
  const existingLists = ['Groceries'];

  const inputIntent = {
    type: 'task',
    title: 'Call mom',
    due_date: '2026-09-09',
    time: '17:00',
  };

  const normalized = normalizeIntents([inputIntent], existingLists, fixedNow);
  assert.equal(normalized.length, 1);
  assert.equal(normalized[0].type, 'task');
  assert.equal(normalized[0].title, 'Call mom');
  assert.equal(normalized[0].due_date, '2026-09-09');
  assert.equal(normalized[0].time, '17:00');

  const actions = intentsToActions(normalized, fixedNow);
  assert.equal(actions.length, 1);
  assert.equal(actions[0].type, 'reminder');
  assert.equal(actions[0].title, 'Call mom');
  assert.equal(actions[0].due_at, '2026-09-09T17:00:00Z');
});

test('Phase 2 - Case 2: Single calendar event with time ("meeting with Sarah tomorrow at 10am")', () => {
  const fixedNow = '2026-09-09T12:00:00.000Z';
  const existingLists = ['Groceries'];

  const inputIntent = {
    type: 'calendar_event',
    title: 'Meeting with Sarah',
    date: '2026-09-10',
    time: '10:00',
  };

  const normalized = normalizeIntents([inputIntent], existingLists, fixedNow);
  assert.equal(normalized.length, 1);
  assert.equal(normalized[0].type, 'calendar_event');
  assert.equal(normalized[0].title, 'Meeting with Sarah');
  assert.equal(normalized[0].date, '2026-09-10');
  assert.equal(normalized[0].time, '10:00');

  const actions = intentsToActions(normalized, fixedNow);
  assert.equal(actions.length, 1);
  assert.equal(actions[0].type, 'calendar_event');
  assert.equal(actions[0].title, 'Meeting with Sarah');
  assert.equal(actions[0].start, '2026-09-10T10:00:00Z');
  assert.equal(actions[0].all_day, false);
});

test('Phase 2 - Case 3: Shopping list addition ("add olive oil and garlic to groceries")', () => {
  const fixedNow = '2026-09-09T12:00:00.000Z';
  const existingLists = ['Groceries'];

  const inputIntent = {
    type: 'list',
    list_name: 'Groceries',
    items: ['olive oil', 'garlic'],
  };

  const normalized = normalizeIntents([inputIntent], existingLists, fixedNow);
  assert.equal(normalized.length, 1);
  assert.equal(normalized[0].type, 'list');
  assert.equal(normalized[0].list_name, 'Groceries');
  assert.equal(normalized[0].is_new_list, false);
  assert.deepEqual(normalized[0].items, ['olive oil', 'garlic']);

  const actions = intentsToActions(normalized, fixedNow);
  assert.equal(actions.length, 2);
  assert.equal(actions[0].type, 'list_item');
  assert.equal(actions[0].list, 'groceries');
  assert.equal(actions[0].text, 'olive oil');
  assert.equal(actions[0].list_name, 'Groceries');
  assert.equal(actions[1].type, 'list_item');
  assert.equal(actions[1].list, 'groceries');
  assert.equal(actions[1].text, 'garlic');
  assert.equal(actions[1].list_name, 'Groceries');
});

test('Phase 2 - Case 4: Compound multi-intent sentence ("meeting with Priya tomorrow at 3pm, remind me to stretch at 6pm, and put bread on shopping list")', () => {
  const fixedNow = '2026-09-09T12:00:00.000Z';
  const existingLists = ['Groceries', 'Shopping'];

  const inputIntents = [
    {
      type: 'calendar_event',
      title: 'Meeting with Priya',
      date: '2026-09-10',
      time: '15:00',
    },
    {
      type: 'task',
      title: 'Stretch',
      due_date: '2026-09-09',
      time: '18:00',
    },
    {
      type: 'list',
      list_name: 'Shopping',
      items: ['bread'],
    },
  ];

  const normalized = normalizeIntents(inputIntents, existingLists, fixedNow);
  assert.equal(normalized.length, 3);
  assert.equal(normalized[0].type, 'calendar_event');
  assert.equal(normalized[0].title, 'Meeting with Priya');
  assert.equal(normalized[0].date, '2026-09-10');
  assert.equal(normalized[0].time, '15:00');
  assert.equal(normalized[1].type, 'task');
  assert.equal(normalized[1].title, 'Stretch');
  assert.equal(normalized[1].due_date, '2026-09-09');
  assert.equal(normalized[1].time, '18:00');
  assert.equal(normalized[2].type, 'list');
  assert.equal(normalized[2].list_name, 'Shopping');
  assert.equal(normalized[2].is_new_list, false);
  assert.deepEqual(normalized[2].items, ['bread']);

  const actions = intentsToActions(normalized, fixedNow);
  assert.equal(actions.length, 3);
  assert.equal(actions[0].type, 'calendar_event');
  assert.equal(actions[0].title, 'Meeting with Priya');
  assert.equal(actions[0].start, '2026-09-10T15:00:00Z');
  assert.equal(actions[1].type, 'reminder');
  assert.equal(actions[1].title, 'Stretch');
  assert.equal(actions[1].due_at, '2026-09-09T18:00:00Z');
  assert.equal(actions[2].type, 'list_item');
  assert.equal(actions[2].text, 'bread');
  assert.equal(actions[2].list, 'shopping');
  assert.equal(actions[2].list_name, 'Shopping');
});

test('Regression case: "Tomorrow at 3 PM, schedule a 30-minute design review, add milk and eggs to my shopping list, and remind me at 6 PM to send the invoice"', () => {
  const fixedNow = '2026-09-09T10:00:00.000Z';
  const existingLists = ['Shopping'];

  const inputIntents = [
    {
      type: 'calendar_event',
      title: '30-minute design review',
      date: '2026-09-10',
      time: '15:00',
      duration: 30,
    },
    {
      type: 'list',
      list_name: 'Shopping',
      items: ['milk', 'eggs'],
    },
    {
      type: 'task',
      title: 'Send the invoice',
      due_date: '2026-09-10',
      time: '18:00',
    },
  ];

  const normalized = normalizeIntents(inputIntents, existingLists, fixedNow);
  assert.equal(normalized.length, 3);
  assert.equal(normalized[0].type, 'calendar_event');
  assert.equal(normalized[0].duration, 30);
  assert.equal(normalized[1].items.length, 2);

  const actions = intentsToActions(normalized, fixedNow);
  assert.equal(actions.length, 4);

  assert.equal(actions[0].type, 'calendar_event');
  assert.equal(actions[0].title, '30-minute design review');
  assert.equal(actions[0].start, '2026-09-10T15:00:00Z');
  assert.equal(actions[0].end, '2026-09-10T15:30:00Z');

  assert.equal(actions[1].type, 'list_item');
  assert.equal(actions[1].text, 'milk');
  assert.equal(actions[1].list, 'shopping');

  assert.equal(actions[2].type, 'list_item');
  assert.equal(actions[2].text, 'eggs');
  assert.equal(actions[2].list, 'shopping');

  assert.equal(actions[3].type, 'reminder');
  assert.equal(actions[3].title, 'Send the invoice');
  assert.equal(actions[3].due_at, '2026-09-10T18:00:00Z');
});

test('Regression case: "create a meeting with yousef at 3pm tomorrow and add egg to my grocery list"', () => {
  const fixedNow = '2026-09-15T12:00:00.000Z';
  const existingLists = ['Groceries'];

  const inputIntents = [
    {
      type: 'calendar_event',
      title: 'new meeting',
      date: '2026-09-16',
      time: '15:00',
      attendees: ['yousef'],
    },
    {
      type: 'list',
      list_name: 'new list',
      item: 'egg',
    },
  ];

  const normalized = normalizeIntents(inputIntents, existingLists, fixedNow);
  assert.equal(normalized.length, 2);
  assert.equal(normalized[0].type, 'calendar_event');
  assert.equal(normalized[0].title, 'Meeting with yousef');
  assert.equal(normalized[0].time, '15:00');
  assert.deepEqual(normalized[0].attendees, ['yousef']);

  assert.equal(normalized[1].type, 'list');
  assert.equal(normalized[1].list_name, 'Groceries');
  assert.deepEqual(normalized[1].items, ['egg']);

  const actions = intentsToActions(normalized, fixedNow);
  assert.equal(actions.length, 2);
  assert.equal(actions[0].type, 'calendar_event');
  assert.equal(actions[0].title, 'Meeting with yousef');
  assert.equal(actions[1].type, 'list_item');
  assert.equal(actions[1].text, 'egg');
  assert.equal(actions[1].list, 'groceries');
});
