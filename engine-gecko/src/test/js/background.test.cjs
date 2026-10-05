const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/assets/via-engine/background.js'), 'utf8');

function extension() {
  const listeners = {};
  const registrations = [];
  const timers = [];
  const event = name => ({ addListener(fn) { listeners[name] = fn; } });
  const port = { onMessage: event('native'), postMessage(message) {
    if (message.type === 'injection') listeners.native({ type: 'decision', id: message.id,
      payload: { page: 42, phases: { 0: ['window.ran = true'] } } });
    if (message.type === 'request') listeners.native({ type: 'decision', id: message.id,
      page: 42, action: message.url.endsWith('/blocked') ? 'cancel' : 'allow' });
  } };
  const browser = { runtime: { connectNative: () => port, onMessage: event('content') },
    tabs: { onRemoved: event('removed') }, cookies: { getAll: async () => [] },
    contentScripts: { register: async options => {
      const record = { options, removed: false };
      registrations.push(record);
      return { unregister: async () => { record.removed = true; } };
    } },
    webRequest: Object.fromEntries(['onBeforeRequest', 'onHeadersReceived', 'onErrorOccurred',
      'onBeforeSendHeaders'].map(name => [name, event(name)])),
  };
  vm.runInNewContext(source, { browser, URL, console, crypto: { randomUUID: () => 'secret-token' },
    setTimeout: fn => timers.push(fn) });
  return { listeners, registrations, timers };
}

test('early injection handles explicit ports and unregisters after content consumes its plan', async () => {
  const { listeners, registrations } = extension();
  await listeners.onHeadersReceived({ tabId: 5, requestId: 'request', type: 'main_frame',
    statusCode: 200, url: 'http://127.0.0.1:8765/page?query=1' });
  const record = registrations[0];
  assert.equal(record.options.matches[0], 'http://127.0.0.1/*');
  assert.equal(record.options.runAt, 'document_start');
  const context = { location: { href: 'http://127.0.0.1:8765/page?query=1#anchor' } };
  vm.runInNewContext(record.options.js[0].code, context);
  assert.equal(context.viaEarlyPayload.xhrToken, 'secret-token');
  assert.equal(context.viaEarlyPayload.page, undefined);
  await listeners.content({ type: 'injected', registration: 'request' }, { tab: { id: 6 } });
  assert.equal(record.removed, false);
  await listeners.content({ type: 'injected', registration: 'request' }, { tab: { id: 5 } });
  assert.equal(record.removed, true);
});

test('filter decisions cancel requests and native page attribution survives navigation', async () => {
  const { listeners } = extension();
  const result = await listeners.onBeforeRequest({ tabId: 5, url: 'https://test/blocked', type: 'image' });
  assert.equal(result.cancel, true);
});

test('sync XHR rewrites restricted headers and never sends the internal marker', () => {
  const { listeners } = extension();
  const marker = 'secret-token ' + encodeURIComponent(JSON.stringify({ 'User-Agent': 'custom', Referer: 'https://test/' }));
  const result = listeners.onBeforeSendHeaders({ requestHeaders: [
    { name: 'X-Via-Sync-Xhr', value: marker }, { name: 'User-Agent', value: 'default' }, { name: 'Accept', value: '*/*' },
  ] });
  assert.equal(result.requestHeaders.some(h => h.name.toLowerCase() === 'x-via-sync-xhr'), false);
  assert.equal(result.requestHeaders.find(h => h.name === 'User-Agent').value, 'custom');
  assert.equal(result.requestHeaders.find(h => h.name === 'Referer').value, 'https://test/');
  assert.equal(listeners.onBeforeSendHeaders({ requestHeaders: [{ name: 'X-Via-Sync-Xhr', value: 'invalid' }] }).cancel, true);
});
