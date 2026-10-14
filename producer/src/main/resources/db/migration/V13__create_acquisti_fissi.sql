CREATE TABLE acquisti_fissi (
    id UUID PRIMARY KEY,
    utente_id BIGINT NOT NULL REFERENCES utenti(id),
    prodotto_id BIGINT NOT NULL REFERENCES prodotti(id),
    admin_id BIGINT NOT NULL REFERENCES utenti(id),
    quantita INT NOT NULL CHECK (quantita BETWEEN 1 AND 100),
    prezzo_unitario NUMERIC(12,2) NOT NULL CHECK (prezzo_unitario > 0),
    totale NUMERIC(12,2) NOT NULL CHECK (totale > 0),
    acquistato_at TIMESTAMPTZ NOT NULL,
    CHECK (totale = prezzo_unitario * quantita),
    CHECK (utente_id <> admin_id)
);
CREATE INDEX idx_acquisti_utente_data ON acquisti_fissi(utente_id, acquistato_at DESC);
