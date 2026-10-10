package it.esercitazione.liveauction.consumer.auth;

import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.dto.LoginRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;

@Service
public class AuthSessionService {
    public static final String ATTRIBUTE = AuthSessionService.class.getName() + ".auth";
    private final ProducerClient producer;
    private final Clock clock;

    @Autowired
    public AuthSessionService(ProducerClient producer) {
        this(producer, Clock.systemUTC());
    }

    AuthSessionService(ProducerClient producer, Clock clock) {
        this.producer = producer;
        this.clock = clock;
    }

    public SessionAuth login(HttpSession session, String username, String password) {
        SessionAuth auth = SessionAuth.from(producer.login(new LoginRequest(username, password)));
        session.setAttribute(ATTRIBUTE, auth);
        return auth;
    }

    public SessionAuth current(HttpSession session) {
        SessionAuth auth = (SessionAuth) session.getAttribute(ATTRIBUTE);
        if (auth == null) return null;
        if (auth.expiresAt().isAfter(Instant.now(clock).plusSeconds(15))) return auth;
        return refresh(session, auth);
    }

    /**
     * Runs one protected Producer call with the access token kept in the server session.
     * On HTTP 401, rotates both tokens through refresh and retries the call once.
     * A second authentication failure invalidates the Consumer session.
     */
    public <T> T withAccess(HttpSession session, Function<String, T> operation) {
        SessionAuth auth = current(session);
        if (auth == null) throw new SessionExpiredException();
        try {
            return operation.apply(auth.accessToken());
        } catch (ProducerException ex) {
            if (ex.status() != 401) throw ex;
            try {
                return operation.apply(refresh(session, auth).accessToken());
            } catch (ProducerException retry) {
                if (retry.status() == 401 || retry.status() == 403) {
                    clear(session);
                    throw new SessionExpiredException();
                }
                throw retry;
            }
        }
    }

    /** Refreshes once per session, so concurrent requests do not reuse an already rotated refresh token. */
    private SessionAuth refresh(HttpSession session, SessionAuth old) {
        synchronized (session) {
            SessionAuth latest = (SessionAuth) session.getAttribute(ATTRIBUTE);
            if (latest == null) throw new SessionExpiredException();
            // Another request may have refreshed while this one waited for the session lock.
            if (!latest.refreshToken().equals(old.refreshToken())) return latest;
            if (!old.refreshExpiresAt().isAfter(Instant.now(clock))) {
                clear(session);
                throw new SessionExpiredException();
            }
            try {
                SessionAuth renewed = SessionAuth.from(producer.refresh(old.refreshToken()));
                if (!renewed.userId().equals(old.userId())) {
                    clear(session);
                    throw new SessionExpiredException();
                }
                session.setAttribute(ATTRIBUTE, renewed);
                return renewed;
            } catch (ProducerException ex) {
                if (ex.status() == 401 || ex.status() == 403) {
                    clear(session);
                    throw new SessionExpiredException();
                }
                throw ex;
            } catch (IllegalStateException ex) {
                clear(session);
                throw new SessionExpiredException();
            }
        }
    }

    public void clear(HttpSession session) {
        try { session.invalidate(); } catch (IllegalStateException ignored) { }
    }
}
