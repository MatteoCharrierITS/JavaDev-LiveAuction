package it.esercitazione.liveauction.producer.portafoglio.model;

/** Valori del ledger definiti dalle migrazioni V8 e V14. */
public enum TipoMovimento {
    IMPOSTAZIONE_SALDO, RISERVA_OFFERTA, RILASCIO_OFFERTA,
    PAGAMENTO_ASTA, INCASSO_ASTA, ACQUISTO_FISSO, INCASSO_FISSO
}
