import { RoomStomp } from './room-stomp.mjs';

const money = value => value == null ? '—' : `${new Intl.NumberFormat('it-IT', {
    minimumFractionDigits: 2, maximumFractionDigits: 2
}).format(Number(value))} CRD`;
const rome = value => value ? new Intl.DateTimeFormat('it-IT', {
    timeZone: 'Europe/Rome', dateStyle: 'medium', timeStyle: 'short'
}).format(Date.parse(value)) + ' (Rome)' : '—';
const validSequence = value => Number.isSafeInteger(value) && value >= 0;

export class AuctionRoom {
    constructor(root, options = {}) {
        this.root = root;
        this.id = Number(root.dataset.auctionId);
        this.websocketUrl = root.dataset.websocketUrl;
        this.fetch = options.fetch || globalThis.fetch.bind(globalThis);
        this.WebSocketType = options.WebSocketType || globalThis.WebSocket;
        this.clock = options.clock || (() => performance.now());
        this.uuid = options.uuid || (() => crypto.randomUUID());
        this.online = false;
        this.synced = false;
        this.sequence = -1;
        this.buffer = [];
        this.pending = null;
        this.feedSequences = new Set();
        this.reconnectDelay = 1000;
        this.generation = 0;
        this.synchronizing = false;
        this.destroyed = false;
        this.form = this.el('bid-form');
        this.form.addEventListener('submit', event => this.bid(event));
    }

    el(id) { return this.root.querySelector(`#${id}`); }
    put(id, value) { this.el(id).textContent = value; }

    async request(path, init = {}) {
        const response = await this.fetch(`/aste/${this.id}/${path}`, {
            credentials: 'same-origin', cache: 'no-store', ...init
        });
        if (!response.ok) {
            const error = new Error(`HTTP ${response.status}`);
            error.status = response.status;
            try { error.code = (await response.json()).code; } catch { /* Status remains enough. */ }
            throw error;
        }
        return response.json();
    }

    async start() {
        await this.sync();
        this.refreshWallet();
        this.timer = setInterval(() => this.tick(), 1000);
        if (this.state && !this.terminal()) this.connect();
    }

    setConnection(text) { this.put('connection-state', text); this.updateBidAccess(); }
    alert(text) { const node = this.el('room-alert'); node.textContent = text; node.hidden = !text; }

    async refreshWallet() {
        const version = (this.walletVersion || 0) + 1;
        this.walletVersion = version;
        try {
            const wallet = await this.request('wallet');
            if (version !== this.walletVersion || this.destroyed) return;
            this.put('wallet-available', money(wallet.saldoDisponibile));
            this.put('wallet-reserved', money(wallet.saldoRiservato));
            this.put('wallet-total', money(wallet.saldoTotale));
            this.put('wallet-status', 'Balance from Producer.');
        } catch (error) {
            if (version !== this.walletVersion) return;
            this.put('wallet-status', error.status === 401 ? 'Session expired. Sign in again.' : 'Balance unavailable.');
        }
    }

    async sync() {
        if (this.synchronizing || this.destroyed) return;
        this.synchronizing = true;
        this.synced = false;
        this.setConnection('Synchronizing…');
        try {
            const snapshot = await this.request('snapshot');
            if (this.destroyed) return;
            if (snapshot.id !== this.id || !validSequence(snapshot.sequence) || !snapshot.serverTime
                    || !snapshot.stato || !snapshot.inizioAt || !snapshot.fineAt) throw new Error('Invalid snapshot');
            this.applySnapshot(snapshot);
            const buffered = this.buffer.splice(0);
            this.synchronizing = false;
            this.synced = true;
            for (const event of buffered) this.handlePublic(event);
            if (this.synced) {
                this.setConnection(this.online ? 'Live' : this.terminal() ? 'Auction ended' : 'Connecting…');
                if (this.online && this.pending && !this.pending.resent) {
                    this.pending.resent = true;
                    this.stomp.sendBid(this.pending.command);
                }
            }
            this.updateBidAccess();
            if (this.timer && !this.online && !this.connecting && !this.stomp && !this.terminal()) this.connect();
        } catch (error) {
            this.synchronizing = false;
            this.setConnection('Synchronizing…');
            if (error.status === 401 || error.status === 403) {
                this.alert('Session expired. Sign in again.');
            } else if (error.status === 404) {
                this.alert('This auction is no longer available.');
            } else {
                this.alert('Auction state unavailable. Retrying…');
                this.retrySync = setTimeout(() => this.sync(), 3000);
            }
        }
    }

    applySnapshot(snapshot) {
        this.state = snapshot;
        this.sequence = snapshot.sequence;
        this.serverTimeAtSample = Date.parse(snapshot.serverTime);
        this.sampleAt = this.clock();
        this.alert('');
        this.render();
    }

    serverNow() { return this.serverTimeAtSample + (this.clock() - this.sampleAt); }
    terminal() { return this.state?.stato === 'CHIUSA' || this.state?.stato === 'ANNULLATA'; }

    render() {
        const s = this.state;
        if (!s) return;
        this.put('auction-state', ({ PROGRAMMATA: 'Scheduled', STANZA_APERTA: 'Waiting for start',
            APERTA: 'Live auction', CHIUSA: 'Closed', ANNULLATA: 'Cancelled' })[s.stato] || s.stato);
        this.put('starting-price', money(s.prezzoIniziale));
        this.put('increment', money(s.incrementoMinimo));
        this.put('current-price', money(s.offertaCorrente ?? s.prezzoIniziale));
        this.put('leader', s.migliorOfferente?.displayName || 'No bids yet');
        this.put('bid-count', String(s.numeroOfferte ?? 0));
        this.put('starts-at', rome(s.inizioAt));
        this.put('ends-at', rome(s.fineAt));
        const terminal = this.el('terminal-result');
        terminal.hidden = !this.terminal();
        terminal.textContent = s.stato === 'ANNULLATA' ? 'This auction was cancelled.'
            : s.stato === 'CHIUSA' ? (s.vincitore
                ? `Winner: ${s.vincitore.displayName} · Final price: ${money(s.prezzoFinale)}`
                : 'Auction closed without bids.') : '';
        this.tick();
        this.updateBidAccess();
    }

    tick() {
        if (!this.state || !Number.isFinite(this.serverTimeAtSample)) return;
        const s = this.state;
        const target = s.stato === 'PROGRAMMATA' ? s.aperturaStanzaAt
            : s.stato === 'STANZA_APERTA' ? s.inizioAt : s.stato === 'APERTA' ? s.fineAt : null;
        this.put('countdown-label', s.stato === 'PROGRAMMATA' ? 'Room opens in'
            : s.stato === 'STANZA_APERTA' ? 'Auction starts in' : 'Ends in');
        const seconds = target ? Math.max(0, Math.ceil((Date.parse(target) - this.serverNow()) / 1000)) : 0;
        this.put('countdown', target ? `${Math.floor(seconds / 60)}m ${String(seconds % 60).padStart(2, '0')}s`
            + (seconds === 0 ? ' · Awaiting server update' : '') : '—');
        this.el('countdown').classList.toggle('urgent', s.stato === 'APERTA' && seconds <= 20 && seconds > 0);
        if (target && seconds === 0 && !this.expiryCheck) {
            this.expiryCheck = setTimeout(() => {
                this.expiryCheck = null;
                this.sync();
                if (s.stato === 'PROGRAMMATA' && !this.online) this.connect();
            }, 1500);
        }
        this.updateBidAccess();
    }

    updateBidAccess() {
        const s = this.state;
        const allowed = Boolean(this.synced && this.online && !this.pending && s?.stato === 'APERTA'
            && s.offerteConsentite !== false && this.serverNow() < Date.parse(s.fineAt));
        this.el('bid-amount').disabled = !allowed;
        this.el('bid-submit').disabled = !allowed;
        if (this.pending) this.put('bid-status', 'Awaiting Producer confirmation…');
        else if (this.bidFeedback) this.put('bid-status', this.bidFeedback);
        else if (s?.stato === 'STANZA_APERTA') this.put('bid-status', 'The room is open. Bidding begins when the Producer starts the auction.');
        else if (s?.stato === 'PROGRAMMATA') this.put('bid-status', 'The room has not opened yet.');
        else if (this.terminal()) this.put('bid-status', 'Bidding has ended.');
        else if (!allowed) this.put('bid-status', 'Bidding is unavailable while the room synchronizes or reconnects.');
        else this.put('bid-status', `Minimum bid: ${money(s.offertaCorrente == null ? s.prezzoIniziale : Number(s.offertaCorrente) + Number(s.incrementoMinimo))}.`);
    }

    async connect() {
        if (this.destroyed || this.online || this.connecting || this.terminal()) return;
        this.connecting = true;
        this.setConnection('Connecting…');
        const generation = ++this.generation;
        try {
            const response = await this.request('ticket', { method: 'POST', headers: {
                [this.root.dataset.csrfHeader]: this.root.dataset.csrfToken
            }});
            if (generation !== this.generation || this.destroyed) return;
            if (!response.ticket) throw new Error('Ticket unavailable');
            const url = new URL(this.websocketUrl);
            url.searchParams.set('ticket', response.ticket);
            // The short-lived ticket remains only in this transport instance.
            this.stomp = new RoomStomp(url.toString(), this.id, {
                connected: () => {
                    if (generation !== this.generation) return;
                    this.online = true;
                    this.connecting = false;
                    this.reconnectDelay = 1000;
                    this.pending && (this.pending.resent = false);
                    this.sync();
                    this.refreshWallet();
                },
                message: (message, subscription) => {
                    if (generation === this.generation) this.message(message, subscription);
                },
                error: () => { if (generation === this.generation) this.alert('Connection error. Reconnecting…'); },
                closed: () => { if (generation === this.generation) this.disconnected(); }
            }, this.WebSocketType);
            this.stomp.connect();
        } catch (error) {
            if (generation !== this.generation) return;
            this.connecting = false;
            if (error.status === 409 && error.code === 'STANZA_NON_APERTA') {
                this.setConnection('Waiting for room opening');
                this.scheduleReconnect(Math.max(1000, Math.min(30000,
                    Date.parse(this.state?.aperturaStanzaAt) - this.serverNow() + 500)));
            } else if (error.status === 401 || error.status === 403) {
                this.setConnection('Access expired');
                this.alert('Your session is no longer authorized. Sign in again.');
            } else if (error.status === 404 || this.terminal()) {
                this.sync();
            } else {
                this.disconnected();
            }
        }
    }

    scheduleReconnect(delay) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = setTimeout(() => this.connect(), delay);
    }

    disconnected() {
        this.online = false;
        this.connecting = false;
        this.synced = false;
        this.put('presence-status', 'Presence unavailable while disconnected.');
        this.el('participants').replaceChildren();
        this.put('participant-count', '—');
        this.setConnection(this.terminal() ? 'Auction ended' : 'Reconnecting…');
        if (!this.destroyed && !this.terminal()) {
            this.scheduleReconnect(this.reconnectDelay);
            this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000);
        }
    }

    message(message, subscription) {
        if (message.auctionId != null && Number(message.auctionId) !== this.id) return;
        if (['PRESENCE_SNAPSHOT', 'USER_JOINED', 'USER_LEFT'].includes(message.type)) {
            if (message.type === 'PRESENCE_SNAPSHOT' && subscription !== 'private') return;
            this.presence(message);
            return;
        }
        if (subscription === 'private') this.privateMessage(message);
        else if (subscription === 'auction') this.handlePublic(message);
    }

    presence(message) {
        if (!Array.isArray(message.participants) || !Number.isSafeInteger(message.participantCount)) return;
        this.put('participant-count', String(message.participantCount));
        this.put('presence-status', message.participantCount ? '' : 'No participants yet.');
        const list = this.el('participants');
        list.replaceChildren(...message.participants.map(name => {
            const item = document.createElement('li'); item.textContent = name; return item;
        }));
    }

    privateMessage(message) {
        if (!['BID_CONFIRMED', 'BID_REJECTED'].includes(message.type)) return;
        if (!this.pending) {
            if (message.type === 'BID_REJECTED') this.alert(message.message || 'Producer rejected a command.');
            return;
        }
        if (message.clientBidId != null && message.clientBidId !== this.pending.command.clientBidId) return;
        this.pending = null;
        if (message.type === 'BID_CONFIRMED') {
            this.bidFeedback = message.duplicata ? 'Previous bid confirmed; no second bid placed.'
                : message.ritirata ? 'This bid was previously withdrawn.' : 'Bid confirmed by Producer.';
            this.el('bid-amount').value = '';
            if (message.snapshotRequired || message.stato?.sequence > this.sequence) this.sync();
        } else {
            this.bidFeedback = `Bid rejected: ${message.message || message.code || 'Producer rejected the bid.'}`;
            if (message.snapshotRequired) this.sync();
        }
        this.refreshWallet();
        this.updateBidAccess();
    }

    handlePublic(event) {
        if (!validSequence(event.sequence) || Number(event.auctionId) !== this.id) return;
        if (this.synchronizing || !this.synced) { this.buffer.push(event); return; }
        if (['ROOM_OPENED', 'AUCTION_STARTED', 'BID_ACCEPTED', 'AUCTION_SNAPSHOT',
            'AUCTION_CLOSED', 'AUCTION_CANCELLED'].includes(event.type)
                && !this.feedSequences.has(event.sequence)) {
            this.feedSequences.add(event.sequence);
            this.feed(event);
        }
        if (event.sequence <= this.sequence) return;
        if (event.sequence !== this.sequence + 1) {
            this.buffer.push(event);
            this.sync();
            return;
        }
        this.sequence = event.sequence;
        this.state.sequence = event.sequence;
        if (event.serverTime) {
            this.serverTimeAtSample = Date.parse(event.serverTime);
            this.sampleAt = this.clock();
        }
        if (event.stato) this.state.stato = event.stato;
        if (event.fineAt) this.state.fineAt = event.fineAt;
        if (Object.hasOwn(event, 'offertaCorrente')) this.state.offertaCorrente = event.offertaCorrente;
        if (Object.hasOwn(event, 'offerenteDisplay')) this.state.migliorOfferente = event.offerenteDisplay
            ? { displayName: event.offerenteDisplay } : null;
        if (event.numeroOfferte != null) this.state.numeroOfferte = event.numeroOfferte;
        this.state.offerteConsentite = this.state.stato === 'APERTA';
        if (event.type === 'AUCTION_CLOSED') {
            this.state.vincitore = event.vincitoreDisplay ? { displayName: event.vincitoreDisplay } : null;
            this.state.prezzoFinale = event.offertaCorrente;
            this.refreshWallet();
        }
        // Another bidder can release this user's reserve, including in another tab.
        if (event.type === 'BID_ACCEPTED' || event.type === 'AUCTION_SNAPSHOT') this.refreshWallet();
        this.render();
        if (this.terminal()) this.stomp?.close();
    }

    feed(event) {
        const item = document.createElement('li');
        const detail = event.type === 'BID_ACCEPTED'
            ? `${event.offerenteDisplay || 'Bidder'} bid ${money(event.importo ?? event.offertaCorrente)}`
                + (event.extensionSeconds ? ` (+${event.extensionSeconds}s)` : '')
            : ({ ROOM_OPENED: 'Room opened', AUCTION_STARTED: 'Auction started',
                AUCTION_SNAPSHOT: 'Auction state updated',
                AUCTION_CLOSED: event.vincitoreDisplay
                    ? `Auction closed · Winner: ${event.vincitoreDisplay} · ${money(event.offertaCorrente)}`
                    : 'Auction closed without bids',
                AUCTION_CANCELLED: 'Auction cancelled' })[event.type];
        item.textContent = `${rome(event.serverTime)} · ${detail}`;
        this.el('event-feed').prepend(item);
    }

    bid(event) {
        event.preventDefault();
        if (!this.synced || !this.online || this.pending || this.el('bid-submit').disabled) return;
        const raw = this.el('bid-amount').value.trim().replace(',', '.');
        if (!/^(?:0|[1-9]\d{0,9})(?:\.\d{1,2})?$/.test(raw) || Number(raw) <= 0) {
            this.put('bid-status', 'Enter a positive amount with at most two decimal places.'); return;
        }
        const command = { type: 'PLACE_BID', clientBidId: this.uuid(), importo: Number(raw),
            knownSequence: this.sequence };
        this.bidFeedback = null;
        this.pending = { command, resent: false };
        if (!this.stomp.sendBid(command)) { this.pending = null; this.disconnected(); return; }
        this.updateBidAccess();
    }

    stop() {
        this.destroyed = true;
        this.generation++;
        clearInterval(this.timer);
        clearTimeout(this.reconnectTimer);
        clearTimeout(this.retrySync);
        clearTimeout(this.expiryCheck);
        this.stomp?.close();
    }
}

const root = document.querySelector('#auction-room');
if (root) {
    const room = new AuctionRoom(root);
    room.start();
    window.addEventListener('pagehide', () => room.stop(), { once: true });
}
