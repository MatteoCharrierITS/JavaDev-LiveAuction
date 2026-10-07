package it.esercitazione.liveauction.producer.auth.events;

/** Hook sincrono nella transazione di eliminazione, prima dell'anonimizzazione. */
public record EliminazioneUtenteRichiesta(long utenteId) {}
