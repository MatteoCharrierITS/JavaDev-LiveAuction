package it.esercitazione.liveauction.producer.auth.services;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketTicketStore {
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConcurrentHashMap<String, TicketData> tickets = new ConcurrentHashMap<>();

    public IssuedTicket issue(long userId, UUID authSessionId, long auctionId, Instant now) {
        clearExpired(now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(TTL);
        tickets.put(hash(value), new TicketData(userId, authSessionId, auctionId, expiresAt));
        return new IssuedTicket(value, expiresAt);
    }

    public Optional<TicketData> consume(String value, Instant now) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        TicketData ticket = tickets.remove(hash(value));
        if (ticket == null || !now.isBefore(ticket.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    @Scheduled(fixedDelay = 60_000)
    public void clearExpired() {
        clearExpired(Instant.now());
    }

    private void clearExpired(Instant now) {
        tickets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 non disponibile", exception);
        }
    }

    public record IssuedTicket(String ticket, Instant expiresAt) {}

    public record TicketData(long userId, UUID authSessionId, long auctionId, Instant expiresAt) {}
}
