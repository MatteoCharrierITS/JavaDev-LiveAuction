package it.esercitazione.liveauction.producer.websocket;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class PresenceRegistry {
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<Long, Map<Long, Participant>> rooms = new HashMap<>();

    public synchronized JoinResult join(long auctionId, long userId, String displayName,
                                        String sessionId) {
        Session existing = sessions.get(sessionId);
        if (existing != null && (existing.auctionId() != auctionId || existing.userId() != userId)) {
            throw new AccessDeniedException("Sessione associata a un'altra stanza");
        }
        Map<Long, Participant> participants = rooms.computeIfAbsent(auctionId,
                ignored -> new LinkedHashMap<>());
        Participant participant = participants.computeIfAbsent(userId,
                ignored -> new Participant(displayName));
        boolean firstSession = participant.sessionIds.add(sessionId);
        sessions.putIfAbsent(sessionId, new Session(auctionId, userId));
        PresenceSnapshot snapshot = snapshot(auctionId, participants);
        PresenceChange change = firstSession && participant.sessionIds.size() == 1
                ? new PresenceChange("USER_JOINED", auctionId, participant.displayName,
                        snapshot.participantCount(), snapshot.participants(), Instant.now())
                : null;
        return new JoinResult(snapshot, Optional.ofNullable(change));
    }

    public synchronized Optional<PresenceChange> leave(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        Map<Long, Participant> participants = rooms.get(session.auctionId());
        if (participants == null) {
            return Optional.empty();
        }
        Participant participant = participants.get(session.userId());
        if (participant == null || !participant.sessionIds.remove(sessionId)
                || !participant.sessionIds.isEmpty()) {
            return Optional.empty();
        }
        participants.remove(session.userId());
        PresenceSnapshot snapshot = snapshot(session.auctionId(), participants);
        if (participants.isEmpty()) {
            rooms.remove(session.auctionId());
        }
        return Optional.of(new PresenceChange("USER_LEFT", session.auctionId(),
                participant.displayName, snapshot.participantCount(), snapshot.participants(),
                Instant.now()));
    }

    private static PresenceSnapshot snapshot(long auctionId, Map<Long, Participant> participants) {
        List<String> names = new ArrayList<>(participants.size());
        participants.values().forEach(participant -> names.add(participant.displayName));
        return new PresenceSnapshot("PRESENCE_SNAPSHOT", auctionId, participants.size(),
                List.copyOf(names), Instant.now());
    }

    private record Session(long auctionId, long userId) {}

    private static final class Participant {
        private final String displayName;
        private final Set<String> sessionIds = new HashSet<>();

        private Participant(String displayName) {
            this.displayName = displayName;
        }
    }

    public record JoinResult(PresenceSnapshot snapshot, Optional<PresenceChange> change) {}

    public record PresenceSnapshot(String type, long auctionId, int participantCount,
                                   List<String> participants, Instant serverTime) {}

    public record PresenceChange(String type, long auctionId, String displayName,
                                 int participantCount, List<String> participants,
                                 Instant serverTime) {}
}
