ALTER TABLE movimenti_portafoglio DROP CONSTRAINT movimenti_portafoglio_tipo_check;
ALTER TABLE movimenti_portafoglio ADD CONSTRAINT movimenti_portafoglio_tipo_check CHECK (tipo IN (
    'IMPOSTAZIONE_SALDO', 'RISERVA_OFFERTA', 'RILASCIO_OFFERTA',
    'PAGAMENTO_ASTA', 'INCASSO_ASTA', 'ACQUISTO_FISSO', 'INCASSO_FISSO', 'CREDITO_INIZIALE'
));
