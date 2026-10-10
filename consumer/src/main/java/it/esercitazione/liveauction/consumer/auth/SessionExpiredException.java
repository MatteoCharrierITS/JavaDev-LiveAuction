package it.esercitazione.liveauction.consumer.auth;

public final class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() { super("Session expired"); }
}
