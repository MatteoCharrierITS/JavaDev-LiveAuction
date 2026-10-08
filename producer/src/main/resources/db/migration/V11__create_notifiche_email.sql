CREATE TABLE notifiche_email (
    asta_id BIGINT PRIMARY KEY REFERENCES aste(id) ON DELETE CASCADE,
    stato VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (stato IN ('PENDING', 'SENT', 'SKIPPED')),
    tentativi INTEGER NOT NULL DEFAULT 0 CHECK (tentativi >= 0),
    prossimo_tentativo_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inviata_at TIMESTAMPTZ,
    ultimo_errore VARCHAR(50),
    data_creazione TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifiche_email_pending ON notifiche_email(prossimo_tentativo_at, asta_id)
    WHERE stato = 'PENDING';
