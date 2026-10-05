package it.esercitazione.liveauction.producer.asta;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessoStanzaService {
    private final JdbcTemplate jdbc;

    public void verificaAccesso(long astaId, Instant now) {
        List<Stanza> stanze = jdbc.query("SELECT inizio_at, stato FROM aste WHERE id = ?",
                (rs, row) -> new Stanza(rs.getTimestamp("inizio_at").toInstant(),
                        rs.getString("stato")), astaId);
        if (stanze.isEmpty()) {
            throw problema(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA");
        }
        Stanza stanza = stanze.getFirst();
        if (now.isBefore(stanza.inizioAt().minus(3, ChronoUnit.MINUTES))
                || "ANNULLATA".equals(stanza.stato())) {
            throw problema(HttpStatus.CONFLICT, "STANZA_NON_APERTA");
        }
    }

    private static ErrorResponseException problema(HttpStatus status, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, code);
        detail.setProperty("code", code);
        return new ErrorResponseException(status, detail, null);
    }

    private record Stanza(Instant inizioAt, String stato) {}
}
