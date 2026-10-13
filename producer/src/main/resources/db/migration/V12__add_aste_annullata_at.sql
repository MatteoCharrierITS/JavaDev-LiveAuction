-- Mantiene distinta la data di annullamento da quella del settlement.
ALTER TABLE aste ADD COLUMN annullata_at TIMESTAMPTZ;

CREATE INDEX idx_aste_storico ON aste
    ((COALESCE(annullata_at, chiusa_at, data_creazione)) DESC, id DESC)
    WHERE stato IN ('CHIUSA', 'ANNULLATA');
