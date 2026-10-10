(() => {
    const lobby = document.querySelector('.auction-lobby');
    if (!lobby) return;

    const serverAtRender = Date.parse(lobby.dataset.serverTime);
    if (!Number.isFinite(serverAtRender)) return;
    const started = performance.now();
    const serverNow = () => serverAtRender + (performance.now() - started);

    const formatter = new Intl.DateTimeFormat('it-IT', {
        timeZone: 'Europe/Rome', dateStyle: 'medium', timeStyle: 'short'
    });
    lobby.querySelectorAll('time.auction-time[datetime]').forEach((element) => {
        const timestamp = Date.parse(element.dateTime);
        if (Number.isFinite(timestamp)) element.textContent = formatter.format(timestamp) + ' (Rome)';
    });

    function updateCountdowns() {
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
                ? '0s · Awaiting server update' : parts.join(' ');
        });
    }
    updateCountdowns();
    window.setInterval(updateCountdowns, 1000);
})();
