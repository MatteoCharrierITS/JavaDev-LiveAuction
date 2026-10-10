package it.esercitazione.liveauction.consumer.client;

public final class ProducerException extends RuntimeException {
    private final int status;
    private final String code;

    public ProducerException(int status, String code) {
        super("Producer returned HTTP " + status);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
