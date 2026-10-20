const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/site.js'), 'utf8');

function element(extra = {}) {
    const handlers = {}, attrs = {}, classes = new Set();
    return Object.assign({ dataset: {}, handlers, attrs, classes,
        addEventListener(type, callback) { handlers[type] = callback; },
        setAttribute(name, value) { attrs[name] = value; },
        getAttribute(name) { return attrs[name] ?? null; },
        removeAttribute(name) { delete attrs[name]; },
        classList: { add(name) { classes.add(name); }, toggle(name, on) { on ? classes.add(name) : classes.delete(name); } }
    }, extra);
}
function run({ nav = null, purchase = null, images = [], forms = [] } = {}) {
    const events = {};
    vm.runInNewContext(script, {
        document: {
            querySelector: selector => selector === '.site-nav' ? nav : purchase,
            querySelectorAll: selector => selector === '[data-product-image]' ? images : forms
        },
        window: { addEventListener(type, callback) { (events[type] ??= []).push(callback); } },
        Intl, Number, String
    });
    return events;
}

test('mobile menu reports its state and Escape closes it with focus restored', () => {
    const button = element({ focus() { this.focused = true; } }), links = element();
    const nav = element({ querySelector: selector => selector === '.nav-toggle' ? button : links });
    run({ nav });
    assert.equal(nav.dataset.enhanced, 'true');
    button.handlers.click();
    assert.equal(button.attrs['aria-expanded'], 'true');
    assert.ok(links.classes.has('is-open'));
    nav.handlers.keydown({ key: 'Escape' });
    assert.equal(button.attrs['aria-expanded'], 'false');
    assert.equal(links.classes.has('is-open'), false);
    assert.equal(button.focused, true);
});

test('estimated purchase total updates without modifying quantity or idempotency key', () => {
    const quantity = element({ value: '2', validity: { valid: true } }), output = element();
    const purchase = element({ dataset: { unitPrice: '29.90' }, clientPurchaseId: 'unchanged',
        querySelector: selector => selector === '[name=quantita]' ? quantity : output });
    run({ purchase });
    assert.equal(output.textContent, '59,80 CRD');
    quantity.value = '3'; quantity.handlers.input();
    assert.equal(output.textContent, '89,70 CRD');
    assert.equal(quantity.value, '3');
    assert.equal(purchase.clientPurchaseId, 'unchanged');
    quantity.validity.valid = false; quantity.handlers.input();
    assert.equal(output.textContent, '—');
});

test('pending form disables its submit button and resets on back navigation', () => {
    const button = element({ textContent: 'Confirm purchase', disabled: false });
    const form = element({ dataset: { submitLabel: 'Confirming…' }, checkValidity: () => true, querySelector: () => button });
    const events = run({ forms: [form] });
    form.handlers.submit();
    assert.equal(button.disabled, true);
    assert.equal(button.textContent, 'Confirming…');
    assert.equal(form.attrs['aria-busy'], 'true');
    events.pageshow.forEach(callback => callback());
    assert.equal(button.disabled, false);
    assert.equal(button.textContent, 'Confirm purchase');
    assert.equal(form.attrs['aria-busy'], undefined);
});

test('a missing image falls back without affecting page actions', () => {
    const figure = element(), image = element({ complete: true, naturalWidth: 0, closest: () => figure });
    run({ images: [image] });
    assert.ok(figure.classes.has('is-image-error'));
    image.handlers.error();
    assert.equal(figure.classes.size, 1);
});

test('pages without navigation or purchase forms initialize safely', () => {
    assert.doesNotThrow(() => run());
});
