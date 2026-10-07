package it.esercitazione.liveauction.producer.auth.services;

import it.esercitazione.liveauction.producer.auth.events.EliminazioneUtenteRichiesta;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EliminazioneUtenteService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventi;

    @Transactional
    @PreAuthorize("authentication.token.subject == #p0.toString()")
    public void elimina(long userId) {
        // I domini interessati completano la pulizia nella stessa transazione.
        eventi.publishEvent(new EliminazioneUtenteRichiesta(userId));
        String anonymousId = UUID.randomUUID().toString();
        String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        jdbc.update("""
                UPDATE utenti
                SET attivo = FALSE,
                    username = ?,
                    email = ?,
                    password_hash = ?
                WHERE id = ? AND attivo = TRUE
                """, "deleted_" + anonymousId, "deleted_" + anonymousId + "@example.invalid",
                passwordHash, userId);
        jdbc.update("""
                UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP
                WHERE utente_id = ? AND revoked_at IS NULL
                """, userId);
    }
}
