package it.esercitazione.liveauction.producer.portafoglio;

import it.esercitazione.liveauction.producer.auth.requests.RegistrazioneRequest;
import it.esercitazione.liveauction.producer.auth.services.RegistrazioneService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "app.notifiche.email.enabled=false", "app.portafoglio.saldo-iniziale=4321.25"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class RegistrazioneCreditoIntegrationTests {
    @Autowired RegistrazioneService registrazione;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void customCreditAndRegistrationAreAtomicAndCannotBeClaimedTwice() {
        String name = "bonus_" + UUID.randomUUID().toString().substring(0, 12);
        var request = new RegistrazioneRequest(name, name + "@example.com", "TestPassword123!");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var registered = registrazione.registra(request);
            assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id = ?",
                    BigDecimal.class, registered.id())).isEqualByComparingTo("4321.25");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimenti_portafoglio WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?) AND tipo = 'CREDITO_INIZIALE'",
                    Long.class, registered.id())).isEqualTo(1);
            assertThatThrownBy(() -> registrazione.registra(request))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM utenti WHERE username = ?", Long.class, name)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM portafogli p JOIN utenti u ON u.id = p.utente_id WHERE u.username = ?", Long.class, name)).isZero();
    }
}
