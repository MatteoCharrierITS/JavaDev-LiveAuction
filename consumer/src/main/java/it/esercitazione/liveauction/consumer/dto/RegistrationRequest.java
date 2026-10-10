package it.esercitazione.liveauction.consumer.dto;

public record RegistrationRequest(String username, String email, String password) {
    @Override public String toString() { return "RegistrationRequest[username=" + username + "]"; }
}
