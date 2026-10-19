(() => {
    const target = document.getElementById('nav-balance-amount');
    if (!target) return;
    fetch(target.dataset.url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
        .then((response) => (response.ok ? response.json() : null))
        .then((data) => { if (data && data.disponibile) target.textContent = data.disponibile + ' ' + (data.valuta || 'CRD'); })
        .catch(() => { /* il saldo resta "CRD": la pagina funziona comunque */ });
})();
