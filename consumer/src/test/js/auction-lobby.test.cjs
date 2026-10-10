const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('countdown advances from Producer serverTime using elapsed monotonic time', () => {
    let elapsed = 0;
    let tick;
    const remaining = { textContent: '' };
    const countdown = {
        dataset: { target: '2026-10-10T10:01:00Z' },
        querySelector: () => remaining
    };
    const lobby = {
        dataset: { serverTime: '2026-10-10T10:00:00Z' },
        querySelectorAll: (selector) => selector.startsWith('time') ? [] : [countdown]
    };
    const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/auction-lobby.js'), 'utf8');
    vm.runInNewContext(source, {
        document: { querySelector: () => lobby },
        performance: { now: () => elapsed },
        window: { setInterval: (callback) => { tick = callback; } },
        Date: { parse: Date.parse },
        Intl
    });
    assert.equal(remaining.textContent, '1m 0s');
    elapsed = 30_000;
    tick();
    assert.equal(remaining.textContent, '0m 30s');
    elapsed = 60_000;
    tick();
    assert.equal(remaining.textContent, '0s · Awaiting server update');
});
