import test from 'node:test';
import assert from 'node:assert/strict';

globalThis.document = { querySelector: () => null, createElement: () => ({ textContent: '' }) };
const { RoomStomp } = await import('../../main/resources/static/js/room-stomp.mjs');
const { AuctionRoom } = await import('../../main/resources/static/js/auction-room.mjs');

class FakeSocket {
    static OPEN = 1;
    constructor(url) { this.url = url; this.readyState = 1; this.sent = []; FakeSocket.last = this; }
    send(value) { this.sent.push(value); }
    close() { this.readyState = 3; this.onclose?.(); }
}

function fakeRoot() {
    const nodes = new Map();
    const node = id => {
        if (!nodes.has(id)) nodes.set(id, {
            textContent: '', value: '', disabled: false, hidden: false, children: [],
            classList: { toggle() {} }, addEventListener() {}, replaceChildren(...children) { this.children = children; },
            prepend(item) { this.children.unshift(item); }
        });
        return nodes.get(id);
    };
    return { dataset: { auctionId: '42', websocketUrl: 'ws://localhost:8081/ws',
        csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'csrf' }, querySelector: selector => node(selector.slice(1)), node };
}

const snapshot = (state = 'APERTA', sequence = 3) => ({ id: 42, stato: state, sequence,
    serverTime: '2026-10-11T17:00:00Z', aperturaStanzaAt: '2026-10-11T16:57:00Z',
    inizioAt: '2026-10-11T17:00:00Z', fineAt: '2026-10-11T17:07:00Z',
    prezzoIniziale: 50, incrementoMinimo: 1, offertaCorrente: 60, numeroOfferte: 1,
    migliorOfferente: { displayName: 'a***e' }, offerteConsentite: state === 'APERTA' });

test('STOMP subscribes to both destinations before join and routes private replies', () => {
    const seen = [];
    const stomp = new RoomStomp('ws://localhost/ws?ticket=short', 42,
        { connected: () => seen.push('connected'), message: (m, subscription) => seen.push(subscription) }, FakeSocket);
    stomp.connect();
    FakeSocket.last.onopen();
    stomp.receive('CONNECTED\nversion:1.2\n\n\0');
    assert.deepEqual(FakeSocket.last.sent.slice(1, 4).map(s => s.split('\n')[0]),
        ['SUBSCRIBE', 'SUBSCRIBE', 'SEND']);
    assert.match(FakeSocket.last.sent[1], /destination:\/topic\/aste\/42/);
    assert.match(FakeSocket.last.sent[2], /destination:\/user\/queue\/aste/);
    assert.match(FakeSocket.last.sent[3], /destination:\/app\/aste\/42\/join/);
    stomp.receive('MESSAGE\nsubscription:private\ndestination:/queue/aste-user123\n\n{"type":"BID_REJECTED"}\0');
    assert.deepEqual(seen, ['connected', 'private']);
    stomp.close();
});

test('pre-live and offline rooms cannot bid; presence never changes auction sequence', () => {
    const root = fakeRoot();
    const room = new AuctionRoom(root, { clock: () => 0, WebSocketType: FakeSocket });
    room.applySnapshot(snapshot('STANZA_APERTA'));
    room.synced = true; room.online = true;
    room.updateBidAccess();
    assert.equal(root.node('bid-submit').disabled, true);
    room.message({ type: 'PRESENCE_SNAPSHOT', auctionId: 42, participantCount: 1,
        participants: ['a***e'] }, 'private');
    assert.equal(room.sequence, 3);
    assert.equal(root.node('participant-count').textContent, '1');
    room.applySnapshot(snapshot('APERTA'));
    room.updateBidAccess();
    assert.equal(root.node('bid-submit').disabled, false);
    room.disconnected();
    assert.equal(root.node('bid-submit').disabled, true);
    room.stop();
});

test('duplicate and out-of-order events preserve state; gaps require a new snapshot', async () => {
    const root = fakeRoot();
    let calls = 0;
    const room = new AuctionRoom(root, { clock: () => 0,
        fetch: async () => { calls++; return { ok: true, json: async () => snapshot('APERTA', 6) }; } });
    room.applySnapshot(snapshot());
    room.synced = true;
    room.handlePublic({ type: 'BID_ACCEPTED', auctionId: 42, sequence: 3, importo: 60,
        offerenteDisplay: 'a***e', serverTime: '2026-10-11T17:00:01Z' });
    assert.equal(room.sequence, 3);
    assert.equal(root.node('event-feed').children.length, 1);
    room.handlePublic({ type: 'BID_ACCEPTED', auctionId: 42, sequence: 5, importo: 70,
        offerenteDisplay: 'b***b', serverTime: '2026-10-11T17:00:02Z' });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(calls, 1);
    assert.equal(room.sequence, 6);
    assert.equal(root.node('event-feed').children.length, 2);
    room.handlePublic({ type: 'BID_ACCEPTED', auctionId: 42, sequence: 4, importo: 65,
        offerenteDisplay: 'c***c', serverTime: '2026-10-11T17:00:02Z' });
    assert.equal(room.sequence, 6);
    room.stop();
});

test('bid command has a fresh UUID and current knownSequence; private rejection releases pending', () => {
    const root = fakeRoot();
    const room = new AuctionRoom(root, { clock: () => 0, uuid: () => '00000000-0000-4000-8000-000000000001' });
    room.applySnapshot(snapshot()); room.synced = true; room.online = true;
    room.stomp = { sendBid: command => { room.sent = command; return true; }, close() {} };
    root.node('bid-amount').value = '61,50';
    room.updateBidAccess();
    room.bid({ preventDefault() {} });
    assert.deepEqual(room.sent, { type: 'PLACE_BID', clientBidId: '00000000-0000-4000-8000-000000000001',
        importo: 61.5, knownSequence: 3 });
    room.refreshWallet = () => {};
    room.privateMessage({ type: 'BID_REJECTED', clientBidId: room.sent.clientBidId,
        code: 'SALDO_INSUFFICIENTE', message: 'Insufficient balance', snapshotRequired: false });
    assert.equal(room.pending, null);
    assert.match(root.node('bid-status').textContent, /rejected/);
    room.stop();
});

test('reconnection requests a new ticket and synchronizes before bidding', async () => {
    const root = fakeRoot();
    let tickets = 0;
    let snapshots = 0;
    const room = new AuctionRoom(root, { clock: () => 0, WebSocketType: FakeSocket,
        fetch: async (path, init) => ({ ok: true, json: async () => {
            if (path.endsWith('/ticket')) { tickets++; assert.equal(init.headers['X-CSRF-TOKEN'], 'csrf');
                return { ticket: `ticket-${tickets}` }; }
            if (path.endsWith('/snapshot')) { snapshots++; return snapshot(); }
            return { saldoTotale: 100, saldoRiservato: 0, saldoDisponibile: 100 };
        }}) });
    room.applySnapshot(snapshot()); room.synced = true;
    await room.connect();
    assert.match(FakeSocket.last.url, /ticket=ticket-1/);
    FakeSocket.last.onopen();
    FakeSocket.last.onmessage({ data: 'CONNECTED\nversion:1.2\n\n\0' });
    assert.equal(root.node('bid-submit').disabled, true);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(root.node('bid-submit').disabled, false);
    const previous = FakeSocket.last;
    previous.close();
    assert.equal(root.node('bid-submit').disabled, true);
    clearTimeout(room.reconnectTimer);
    await room.connect();
    assert.match(FakeSocket.last.url, /ticket=ticket-2/);
    FakeSocket.last.onopen();
    FakeSocket.last.onmessage({ data: 'CONNECTED\nversion:1.2\n\n\0' });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(snapshots, 2);
    assert.equal(root.node('bid-submit').disabled, false);
    room.stop();
});

test('countdown uses elapsed time since Producer serverTime', () => {
    const root = fakeRoot();
    let elapsed = 0;
    const room = new AuctionRoom(root, { clock: () => elapsed });
    room.applySnapshot(snapshot());
    assert.equal(root.node('countdown').textContent, '7m 00s');
    elapsed = 5000;
    room.tick();
    assert.equal(root.node('countdown').textContent, '6m 55s');
    room.stop();
});

test('private confirmation recognizes duplicate retry without inventing a public bid', () => {
    const root = fakeRoot();
    const room = new AuctionRoom(root, { clock: () => 0 });
    room.applySnapshot(snapshot()); room.synced = true; room.online = true;
    room.pending = { command: { clientBidId: 'same-id', importo: 61, knownSequence: 3 }, resent: true };
    room.refreshWallet = () => {};
    room.privateMessage({ type: 'BID_CONFIRMED', clientBidId: 'same-id', duplicata: true,
        snapshotRequired: false, stato: { sequence: 3 } });
    assert.equal(room.pending, null);
    assert.match(root.node('bid-status').textContent, /no second bid/);
    assert.equal(root.node('event-feed').children.length, 0);
    room.stop();
});

test('accepted public bid updates price, leader, timer and real event feed', () => {
    const root = fakeRoot();
    const room = new AuctionRoom(root, { clock: () => 0 });
    room.applySnapshot(snapshot()); room.synced = true;
    room.handlePublic({ type: 'BID_ACCEPTED', auctionId: 42, sequence: 4,
        serverTime: '2026-10-11T17:01:00Z', stato: 'APERTA', importo: 61,
        offertaCorrente: 61, offerenteDisplay: 'b***b', numeroOfferte: 2,
        fineAt: '2026-10-11T17:07:20Z', extensionSeconds: 20 });
    assert.equal(room.sequence, 4);
    assert.equal(root.node('leader').textContent, 'b***b');
    assert.match(root.node('current-price').textContent, /61,00 CRD/);
    assert.equal(root.node('countdown').textContent, '6m 20s');
    assert.equal(root.node('event-feed').children.length, 1);
    room.stop();
});

test('uncorrelated server rejection is surfaced and STOMP errors close the connection', () => {
    const root = fakeRoot();
    const room = new AuctionRoom(root, { clock: () => 0 });
    room.applySnapshot(snapshot()); room.synced = true;
    room.privateMessage({ type: 'BID_REJECTED', clientBidId: null, message: 'Command invalid' });
    assert.equal(root.node('room-alert').textContent, 'Command invalid');
    room.stop();
    let errors = 0;
    let closed = 0;
    const stomp = new RoomStomp('ws://localhost/ws?ticket=short', 42,
        { error: () => errors++, closed: () => closed++ }, FakeSocket);
    stomp.connect();
    stomp.receive('ERROR\nmessage:denied\n\nRejected\0');
    assert.equal(errors, 1);
    assert.equal(closed, 1);
});
