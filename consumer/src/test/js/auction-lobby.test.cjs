const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/auction-lobby.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

function harness() {
    const env = { elapsed: 0, requests: [], responses: [], timers: new Map(), intervals: new Map(),
        documentEvents: {}, windowEvents: {}, fragments: new Map(), status: { textContent: '' } };
    let id = 0;
    function fragment(sample = '2026-10-10T10:00:00Z', state = 'APERTA', target = '2026-10-10T10:01:00Z') {
        return { dataset: { serverTime: sample }, state, countdowns: target ? [{ dataset: { target },
            remaining: { textContent: '' }, querySelector() { return this.remaining; } }] : [],
            contains: element => element === 'auction-control',
            replaceWith(node) { env.results = node; } };
    }
    env.results = fragment(); env.fragment = fragment;
    env.document = { hidden: false, activeElement: null, querySelector: () => env.lobby,
        addEventListener: (name, callback) => { env.documentEvents[name] = callback; } };
    env.lobby = { dataset: { serverTime: env.results.dataset.serverTime },
        querySelector: selector => selector === '#auction-results' ? env.results : env.status,
        querySelectorAll: selector => selector.startsWith('time') ? [] : env.results.countdowns };
    env.window = { location: { href: 'http://demo.local:8082/aste?stato=APERTA&categoria=office&query=desk&page=1&size=2' },
        setTimeout: (callback, delay) => { env.timers.set(++id, { callback, delay }); return id; },
        clearTimeout: key => env.timers.delete(key),
        setInterval: callback => { env.intervals.set(++id, callback); return id; },
        clearInterval: key => env.intervals.delete(key),
        addEventListener: (name, callback) => { env.windowEvents[name] = callback; },
        fetch: async (url, options) => {
            env.requests.push({ url, options });
            if (!env.responses.length) throw new Error('Network offline');
            return env.responses.shift()(url, options);
        } };
    class Parser { parseFromString(html) { return { querySelector: () => env.fragments.get(html) }; } }
    vm.runInNewContext(source, { document: env.document, window: env.window, performance: { now: () => env.elapsed },
        DOMParser: Parser, URL, AbortController, Date, Intl, Set });
    env.tick = async elapsed => { env.elapsed = elapsed; for (const callback of env.intervals.values()) callback(); await flush(); };
    env.poll = async () => {
        const entry = [...env.timers].find(([, timer]) => timer.delay === 5000 || timer.delay === 10000);
        assert.ok(entry, 'A poll must be scheduled');
        env.timers.delete(entry[0]); await entry[1].callback();
    };
    env.respond = (node, html = 'fragment-' + env.fragments.size) => {
        env.fragments.set(html, node); env.responses.push(async () => ({ ok: true, text: async () => html }));
    };
    return env;
}

test('countdown follows Producer time and requests fresh data at zero', async () => {
    const h = harness();
    assert.equal(h.results.countdowns[0].remaining.textContent, '1m 0s');
    await h.tick(30000); assert.equal(h.results.countdowns[0].remaining.textContent, '0m 30s');
    await h.tick(60000); assert.equal(h.requests.length, 1);
    assert.equal(h.results.countdowns[0].remaining.textContent, '0s · Checking auction status…');
    await h.tick(61000); assert.equal(h.requests.length, 1, 'No request per second at zero');
});

test('poll preserves filters and pagination and replaces only results', async () => {
    const h = harness(); const originalLobby = h.lobby;
    const next = h.fragment('2026-10-10T10:00:10Z'); next.price = 90; next.bids = 3;
    h.respond(next); await h.poll();
    const url = new URL(h.requests[0].url);
    assert.equal(url.searchParams.get('partial'), 'true');
    for (const [key, value] of Object.entries({stato:'APERTA',categoria:'office',query:'desk',page:'1',size:'2'}))
        assert.equal(url.searchParams.get(key), value);
    assert.equal(h.requests[0].options.cache, 'no-store');
    assert.equal(h.requests[0].options.credentials, 'same-origin');
    assert.equal(h.requests[0].options.redirect, 'error');
    assert.equal(h.lobby, originalLobby); assert.equal(h.results, next); assert.equal(h.results.price, 90);
    assert.match(h.status.textContent, /Up to date/);
    assert.equal(next.countdowns[0].remaining.textContent, '0m 50s');
});

test('state changes, closure, cancellation and empty filtered pages follow server fragments', async () => {
    const h = harness();
    for (const state of ['STANZA_APERTA','APERTA','ANNULLATA','CHIUSA','EMPTY']) {
        const next = h.fragment('2026-10-10T10:00:00Z', state,
            ['CHIUSA','ANNULLATA','EMPTY'].includes(state) ? null : '2026-10-10T10:01:00Z');
        h.respond(next); await h.poll(); assert.equal(h.results.state, state);
    }
});

test('network failure keeps old data, reports staleness and recovers', async () => {
    const h = harness(); const previous = h.results;
    await h.poll(); assert.equal(h.results, previous); assert.match(h.status.textContent, /outdated/);
    assert.ok([...h.timers.values()].some(timer => timer.delay === 10000));
    h.respond(h.fragment('2026-10-10T10:00:20Z')); await h.poll();
    assert.notEqual(h.results, previous); assert.match(h.status.textContent, /Up to date/);
});

test('error pages and malformed fragments never replace the list', async () => {
    const h = harness(); const previous = h.results;
    for (const response of [{ok:false,text:async()=>'<error>'},{ok:true,text:async()=>'<login>'}]) {
        h.responses.push(async () => response); await h.poll();
        assert.equal(h.results, previous); assert.match(h.status.textContent, /outdated/);
    }
});

test('focused auction controls pause replacement until interaction ends', async () => {
    const h = harness(); const previous = h.results;
    h.document.activeElement = 'auction-control'; await h.poll();
    assert.equal(h.requests.length, 0); assert.match(h.status.textContent, /paused/);
    h.document.activeElement = null; h.respond(h.fragment()); await h.poll();
    assert.notEqual(h.results, previous);
});

test('focus acquired during a fetch is also preserved', async () => {
    const h = harness(); const previous = h.results;
    h.responses.push(async () => { h.document.activeElement = 'auction-control';
        h.fragments.set('new', h.fragment()); return {ok:true,text:async()=>'new'}; });
    await h.poll(); assert.equal(h.results, previous);
});

test('hidden tabs pause and visibility return refreshes immediately', async () => {
    const h = harness(); h.document.hidden = true; h.documentEvents.visibilitychange();
    assert.equal(h.timers.size, 0); assert.match(h.status.textContent, /hidden/);
    h.respond(h.fragment()); h.document.hidden = false; h.documentEvents.visibilitychange(); await flush();
    assert.equal(h.requests.length, 1);
});

test('requests do not overlap and are aborted on page exit; back navigation resumes', async () => {
    const h = harness(); let finish;
    h.responses.push(() => new Promise(resolve => { finish = resolve; }));
    const pending = h.poll(); await flush(); h.documentEvents.visibilitychange();
    assert.equal(h.requests.length, 1);
    h.windowEvents.pagehide(); assert.equal(h.requests[0].options.signal.aborted, true);
    finish({ok:true,text:async()=>'ignored'}); await pending;
    assert.equal(h.intervals.size, 0); assert.equal(h.timers.size, 0);
    h.respond(h.fragment()); h.windowEvents.pageshow({persisted:true}); await flush();
    assert.equal(h.requests.length, 2); assert.equal(h.intervals.size, 1);
});
