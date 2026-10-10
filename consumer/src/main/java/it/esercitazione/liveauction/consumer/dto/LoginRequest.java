package it.esercitazione.liveauction.consumer.dto;

public record LoginRequest(String username, String password) {
    @Override public String toString() { return "LoginRequest[username=" + username + "]"; }
}
