const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const Cache = vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/assets/via-engine/gm-cache.js'), 'utf8') + '\nViaGmCache');

function page(grants = 15) {
  const writes = [];
  const cache = new Cache((message) => writes.push(JSON.parse(message)));
  cache.reset({ secret: 'token', scripts: { script: { grants, info: '{"scriptHandler":"Via"}',
    values: { saved: '{"nested":[1,true]}' }, resourceText: {}, resourceUrl: {} } } });
  const call = (name, args = {}, secret = 'token', identifier = 'script') =>
    cache.call(JSON.stringify({ secret, identifier, name, arguments: args }), secret);
  return { cache, call, writes };
}

test('persisted values, defaults and writes are synchronous before native acknowledgement', () => {
  const { call, writes } = page();
  assert.equal(call('getValue', { name: 'saved' }), '{"nested":[1,true]}');
  assert.equal(call('getValue', { name: 'missing', value: '42' }), '42');
  assert.equal(call('getValue', { name: 'missing' }), 'undefined');
  call('setValue', { name: 'saved', value: 'false' });
  assert.equal(call('getValue', { name: 'saved' }), 'false');
  call('deleteValue', { name: 'saved' });
  assert.equal(call('listValues'), '');
  assert.deepEqual(writes.map(w => w.name), ['setValue', 'deleteValue']);
});

test('cross-page changes replace the local copy; navigation discards it', () => {
  const { cache, call } = page();
  cache.update('script', 'saved', '100');
  assert.equal(call('getValue', { name: 'saved' }), '100');
  cache.update('script', 'saved', null);
  assert.equal(call('listValues'), '');
  cache.reset(null);
  assert.equal(call('info'), null);
});

test('bridge secrets, unknown scripts and grant masks protect snapshot data and writes', () => {
  const { call, writes } = page(0);
  assert.equal(call('getValue', { name: 'saved' }), 'undefined');
  assert.equal(call('info', {}, 'bad-token'), null);
  assert.equal(call('info', {}, 'token', 'unknown'), null);
  assert.equal(call('info', {}, 'token', '__proto__'), null);
  call('setValue', { name: 'saved', value: '123' });
  call('deleteValue', { name: 'saved' });
  assert.equal(writes.length, 0);
});

test('property-shaped storage keys remain ordinary values', () => {
  const { call } = page();
  call('setValue', { name: '__proto__', value: '"ok"' });
  assert.equal(call('getValue', { name: '__proto__' }), '"ok"');
  assert.equal(call('getValue', { name: 'constructor', value: '7' }), '7');
});
