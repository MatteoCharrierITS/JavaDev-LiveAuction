package it.esercitazione.liveauction.consumer.dto;

public record RefreshRequest(String refreshToken) {
    @Override public String toString() { return "RefreshRequest[redacted]"; }
}
