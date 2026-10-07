-- Stato operativo separato dai dati immutabili dell'offerta accettata.
ALTER TABLE offerte
    ADD COLUMN leader BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ritirata_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_offerte_leader_non_ritirato
        CHECK (NOT leader OR ritirata_at IS NULL);

-- Compatibilità con eventuali offerte già presenti prima di questa migrazione.
UPDATE offerte SET leader = TRUE
WHERE id IN (
    SELECT DISTINCT ON (o.asta_id) o.id
    FROM offerte o JOIN aste a ON a.id = o.asta_id
    WHERE a.stato IN ('PROGRAMMATA', 'STANZA_APERTA', 'APERTA', 'CHIUSA')
      AND o.importo = a.offerta_corrente
    ORDER BY o.asta_id, o.id DESC
);

CREATE UNIQUE INDEX idx_offerte_unico_leader ON offerte(asta_id) WHERE leader;
CREATE INDEX idx_offerte_utente_non_ritirate ON offerte(offerente_id, asta_id) WHERE ritirata_at IS NULL;
