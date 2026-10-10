package it.esercitazione.liveauction.consumer.client;

public final class ProducerUnavailableException extends RuntimeException {
    public ProducerUnavailableException() {
        super("Producer is unavailable");
    }
}
