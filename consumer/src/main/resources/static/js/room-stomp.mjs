// Minimal STOMP 1.2 transport for the Producer's native WebSocket endpoint.
export class RoomStomp {
    constructor(url, auctionId, handlers, WebSocketType = WebSocket) {
        this.url = url;
        this.auctionId = auctionId;
        this.handlers = handlers;
        this.WebSocketType = WebSocketType;
        this.buffer = '';
        this.ready = false;
        this.closed = false;
        this.lastReceived = 0;
    }

    connect() {
        const socket = new this.WebSocketType(this.url);
        this.socket = socket;
        socket.onopen = () => this.frame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '10000,10000' });
        socket.onmessage = (event) => this.receive(String(event.data));
        socket.onerror = () => this.handlers.error?.();
        socket.onclose = () => this.finish();
    }

    frame(command, headers = {}, body = '') {
        if (this.socket?.readyState !== this.WebSocketType.OPEN) return false;
        const lines = [command, ...Object.entries(headers).map(([key, value]) => `${key}:${value}`), '', body];
        this.socket.send(lines.join('\n') + '\0');
        return true;
    }

    sendBid(payload) {
        return this.ready && this.frame('SEND', {
            destination: `/app/aste/${this.auctionId}/offerte`, 'content-type': 'application/json'
        }, JSON.stringify(payload));
    }

    receive(data) {
        this.lastReceived = Date.now();
        this.buffer += data;
        let end;
        while ((end = this.buffer.indexOf('\0')) !== -1) {
            const raw = this.buffer.slice(0, end).replace(/^\n+/, '');
            this.buffer = this.buffer.slice(end + 1);
            if (!raw.trim()) continue;
            const boundary = raw.indexOf('\n\n');
            if (boundary < 0) continue;
            const head = raw.slice(0, boundary).split('\n');
            const command = head.shift().replace(/\r$/, '');
            const headers = Object.fromEntries(head.map(line => {
                const colon = line.indexOf(':');
                return [line.slice(0, colon), line.slice(colon + 1).replace(/\r$/, '')];
            }));
            const body = raw.slice(boundary + 2);
            if (command === 'CONNECTED') {
                this.ready = true;
                this.frame('SUBSCRIBE', { id: 'auction', destination: `/topic/aste/${this.auctionId}`, ack: 'auto' });
                this.frame('SUBSCRIBE', { id: 'private', destination: '/user/queue/aste', ack: 'auto' });
                this.frame('SEND', { destination: `/app/aste/${this.auctionId}/join` });
                this.heartbeat = setInterval(() => {
                    if (Date.now() - this.lastReceived > 30000) this.socket.close();
                    else if (this.socket.readyState === this.WebSocketType.OPEN) this.socket.send('\n');
                }, 10000);
                this.handlers.connected?.();
            } else if (command === 'MESSAGE') {
                try {
                    const message = JSON.parse(body);
                    this.handlers.message?.(message, headers.subscription);
                } catch { this.handlers.error?.(); }
            } else if (command === 'ERROR') {
                this.handlers.error?.();
                this.socket.close();
            }
        }
    }

    finish() {
        if (this.closed) return;
        this.closed = true;
        this.ready = false;
        clearInterval(this.heartbeat);
        this.handlers.closed?.();
    }

    close() {
        this.finish();
        if (this.socket?.readyState === this.WebSocketType.OPEN) this.frame('DISCONNECT');
        this.socket?.close();
    }
}
