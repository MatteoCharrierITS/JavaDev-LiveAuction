(() => {
    const nav = document.querySelector('.site-nav');
    const toggle = nav?.querySelector('.nav-toggle');
    const links = nav?.querySelector('.nav-links');
    if (toggle && links) {
        nav.dataset.enhanced = 'true';
        const setOpen = open => {
            toggle.setAttribute('aria-expanded', String(open));
            links.classList.toggle('is-open', open);
        };
        toggle.addEventListener('click', () => setOpen(toggle.getAttribute('aria-expanded') !== 'true'));
        nav.addEventListener('keydown', event => {
            if (event.key === 'Escape' && toggle.getAttribute('aria-expanded') === 'true') {
                setOpen(false);
                toggle.focus();
            }
        });
    }
    // Le illustrazioni sono locali; un asset mancante non interrompe catalogo o acquisti.
    document.querySelectorAll('[data-product-image]').forEach(img => {
        const fallback = () => img.closest('figure')?.classList.add('is-image-error');
        img.addEventListener('error', fallback);
        if (img.complete && img.naturalWidth === 0) fallback();
    });
    document.querySelectorAll('form[data-submit-label]').forEach(form => {
        const button = form.querySelector('button[type=submit]');
        if (!button) return;
        const label = button.textContent;
        form.addEventListener('submit', () => {
            if (!form.checkValidity()) return;
            button.disabled = true;
            button.textContent = form.dataset.submitLabel;
            form.setAttribute('aria-busy', 'true');
        });
        window.addEventListener('pageshow', () => {
            button.disabled = false;
            button.textContent = label;
            form.removeAttribute('aria-busy');
        });
    });
    const purchase = document.querySelector('[data-unit-price]');
    const quantity = purchase?.querySelector('[name=quantita]');
    const total = purchase?.querySelector('output');
    if (quantity && total) {
        const update = () => {
            const count = Number(quantity.value), price = Number(purchase.dataset.unitPrice);
            total.textContent = Number.isFinite(count) && Number.isFinite(price) && quantity.validity.valid
                ? new Intl.NumberFormat('it-IT', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(price * count) + ' CRD'
                : '—';
        };
        quantity.addEventListener('input', update);
        update();
    }
})();
