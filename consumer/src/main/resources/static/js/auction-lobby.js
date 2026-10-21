(() => {
    const lobby = document.querySelector('.auction-lobby');
    if (!lobby) return;

    let results = lobby.querySelector('#auction-results');
    const status = lobby.querySelector('#lobby-update-status');
    let serverAtRender = Date.parse(results?.dataset.serverTime || lobby.dataset.serverTime);
    if (!Number.isFinite(serverAtRender)) return;
    let started = performance.now();
    const serverNow = () => serverAtRender + (performance.now() - started);

    const formatter = new Intl.DateTimeFormat('it-IT', {
        timeZone: 'Europe/Rome', dateStyle: 'medium', timeStyle: 'short'
    });
    function formatTimes() { lobby.querySelectorAll('time.auction-time[datetime]').forEach((element) => {
        const timestamp = Date.parse(element.dateTime);
        if (Number.isFinite(timestamp)) element.textContent = formatter.format(timestamp) + ' (Rome)';
    }); }
    formatTimes();

    let pending = false;
    let stopped = false;
    let nextPoll;
    let controller;
    const expiredTargets = new Set();
    const setStatus = text => {
        if (status && status.textContent !== text) status.textContent = text;
    };
    function schedule(delay = 5000) {
        window.clearTimeout(nextPoll);
        if (!stopped && !document.hidden) nextPoll = window.setTimeout(refresh, delay);
    }
    async function refresh() {
        if (!results || stopped || document.hidden || pending) return;
        // Do not replace links or ADMIN forms while the user is interacting with them.
        if (results.contains(document.activeElement)) {
            setStatus('Updates paused while using auction controls.');
            schedule();
            return;
        }
        pending = true;
        controller = new AbortController();
        const requestController = controller;
        const timeout = window.setTimeout(() => requestController.abort(), 8000);
        let retry = 5000;
        try {
            const url = new URL(window.location.href);
            url.searchParams.set('partial', 'true');
            const response = await window.fetch(url.toString(), {
                credentials: 'same-origin', cache: 'no-store', redirect: 'error',
                headers: { Accept: 'text/html' }, signal: requestController.signal
            });
            if (!response.ok) throw new Error('Lobby unavailable');
            const html = await response.text();
            if (stopped || document.hidden || requestController.signal.aborted) return;
            const page = new DOMParser().parseFromString(html, 'text/html');
            const fresh = page.querySelector('#auction-results');
            const sample = Date.parse(fresh?.dataset.serverTime);
            if (!fresh || !Number.isFinite(sample)) throw new Error('Invalid lobby response');
            if (results.contains(document.activeElement)) {
                setStatus('Updates paused while using auction controls.');
                return;
            }
            results.replaceWith(fresh);
            results = fresh;
            serverAtRender = sample;
            started = performance.now();
            formatTimes();
            updateCountdowns();
            setStatus('Up to date. Automatic updates every 5 seconds.');
        } catch (error) {
            if (!stopped && !document.hidden) {
                setStatus('Updates unavailable. Displayed data may be outdated. Retrying…');
                retry = 10000;
            }
        } finally {
            window.clearTimeout(timeout);
            pending = false;
            schedule(retry);
        }
    }

    function updateCountdowns() {
        let expired = false;
        lobby.querySelectorAll('.auction-countdown[data-target]').forEach((element) => {
            const target = Date.parse(element.dataset.target);
            if (!Number.isFinite(target)) return;
            const seconds = Math.max(0, Math.ceil((target - serverNow()) / 1000));
            const days = Math.floor(seconds / 86400);
            const hours = Math.floor((seconds % 86400) / 3600);
            const minutes = Math.floor((seconds % 3600) / 60);
            const remainder = seconds % 60;
            const parts = [];
            if (days) parts.push(`${days}d`);
            if (hours || days) parts.push(`${hours}h`);
            parts.push(`${minutes}m`, `${remainder}s`);
            element.querySelector('.auction-remaining').textContent = seconds === 0
                ? '0s · Checking auction status…' : parts.join(' ');
            if (seconds === 0 && !expiredTargets.has(element.dataset.target)) {
                expiredTargets.add(element.dataset.target);
                expired = true;
            }
        });
        if (expired) refresh();
    }
    updateCountdowns();
    let clock = window.setInterval(updateCountdowns, 1000);
    schedule();
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            window.clearTimeout(nextPoll);
            controller?.abort();
            setStatus('Updates paused while this tab is hidden.');
        } else if (!stopped) {
            updateCountdowns();
            refresh();
        }
    });
    window.addEventListener('pagehide', () => {
        stopped = true;
        window.clearTimeout(nextPoll);
        window.clearInterval(clock);
        controller?.abort();
    });
    window.addEventListener('pageshow', event => {
        if (event.persisted) {
            stopped = false;
            clock = window.setInterval(updateCountdowns, 1000);
            refresh();
        }
    });
})();
