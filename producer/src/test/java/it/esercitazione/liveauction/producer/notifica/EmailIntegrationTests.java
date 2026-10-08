package it.esercitazione.liveauction.producer.notifica;

import it.esercitazione.liveauction.producer.asta.events.EventiOffertePublisher;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "app.notifiche.email.enabled=true", "app.notifiche.email.from=aste@example.test"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class EmailIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailQueueService queue;
    @Autowired EmailDeliveryService delivery;
    @Autowired EventiOffertePublisher publisher;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean JavaMailSender mail;
    // I cicli sono chiamati esplicitamente dai test: nessun invio automatico su fixture di altre suite.
    @MockitoBean EmailJob job;
    private Long user;
    private Long category;
    private Long product;
    private Long auction;

    @BeforeEach
    void prepara() {
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        user = jdbc.queryForObject("""
                INSERT INTO utenti(username, email, password_hash, ruolo)
                VALUES (?, ?, 'unused-test', 'USER') RETURNING id
                """, Long.class, "email-" + suffix, suffix + "@example.test");
        category = jdbc.queryForObject("INSERT INTO categorie(nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "Email " + suffix, "email-" + suffix);
        product = jdbc.queryForObject("INSERT INTO prodotti(categoria_id, sku, nome) VALUES (?, ?, 'Laptop email') RETURNING id",
                Long.class, category, "EMAIL-" + suffix);
        Instant start = Instant.now().minusSeconds(600);
        auction = jdbc.queryForObject("""
                INSERT INTO aste(prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at,
                  vincitore_id, offerta_corrente, chiusa_at)
                VALUES (?, ?, 'CHIUSA', 10, ?, ?, ?, 12.50, ?) RETURNING id
                """, Long.class, product, user, Timestamp.from(start), Timestamp.from(start.plusSeconds(420)),
                user, Timestamp.from(start.plusSeconds(420)));
        queue.chiusura(evento());
        jdbc.update("UPDATE notifiche_email SET prossimo_tentativo_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE asta_id = ?", auction);
    }

    @AfterEach
    void pulisci() {
        if (auction != null) jdbc.update("DELETE FROM aste WHERE id = ?", auction);
        if (product != null) jdbc.update("DELETE FROM prodotti WHERE id = ?", product);
        if (category != null) jdbc.update("DELETE FROM categorie WHERE id = ?", category);
        if (user != null) jdbc.update("DELETE FROM utenti WHERE id = ?", user);
    }

    @Test
    void successfulSendPersistsStateAndRepeatedEventsDoNotSendAgain() {
        assertThat(delivery.inviaProssima()).isTrue();
        assertThat(stato()).isEqualTo("SENT");
        assertThat(tentativi()).isEqualTo(1);
        queue.chiusura(evento());
        queue.chiusura(evento());
        assertThat(stato()).isEqualTo("SENT");
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpFailurePersistsRetryWithoutChangingAuctionAndRetrySucceeds() {
        doThrow(new MailSendException("provider-secret-not-for-logs")).doNothing()
                .when(mail).send(any(SimpleMailMessage.class));
        delivery.inviaProssima();
        assertThat(stato()).isEqualTo("PENDING");
        assertThat(tentativi()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ultimo_errore FROM notifiche_email WHERE asta_id = ?", String.class, auction))
                .isEqualTo("SMTP_ERROR");
        assertThat(jdbc.queryForObject("SELECT prossimo_tentativo_at > CURRENT_TIMESTAMP FROM notifiche_email WHERE asta_id = ?",
                Boolean.class, auction)).isTrue();
        assertThat(jdbc.queryForObject("SELECT stato FROM aste WHERE id = ?", String.class, auction)).isEqualTo("CHIUSA");
        jdbc.update("UPDATE notifiche_email SET prossimo_tentativo_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE asta_id = ?", auction);
        delivery.inviaProssima();
        assertThat(stato()).isEqualTo("SENT");
        assertThat(tentativi()).isEqualTo(2);
        verify(mail, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void deletedWinnerIsSkippedWithoutSendingToAnonymizedAddress() {
        jdbc.update("UPDATE utenti SET attivo = FALSE, email = 'deleted@example.invalid' WHERE id = ?", user);
        delivery.inviaProssima();
        assertThat(stato()).isEqualTo("SKIPPED");
        verifyNoInteractions(mail);
    }

    @Test
    void rollbackCreatesNoQueueAndPostCommitListenerCreatesOne() {
        jdbc.update("DELETE FROM notifiche_email WHERE asta_id = ?", auction);
        jdbc.update("UPDATE aste SET stato = 'APERTA', vincitore_id = NULL, chiusa_at = NULL WHERE id = ?", auction);
        var transaction = new TransactionTemplate(manager);
        transaction.executeWithoutResult(status -> {
            conclude();
            publisher.dopoCommit(evento());
            status.setRollbackOnly();
        });
        assertThat(count()).isZero();
        transaction.executeWithoutResult(status -> {
            conclude();
            publisher.dopoCommit(evento());
        });
        assertThat(count()).isEqualTo(1);
        verifyNoInteractions(mail);
    }

    @Test
    void reconciliationRecoversLostEventAndDoesNotQueueAuctionWithoutWinner() {
        jdbc.update("DELETE FROM notifiche_email WHERE asta_id = ?", auction);
        queue.recupera();
        queue.recupera();
        assertThat(count()).isEqualTo(1);
        jdbc.update("DELETE FROM notifiche_email WHERE asta_id = ?", auction);
        jdbc.update("UPDATE aste SET vincitore_id = NULL WHERE id = ?", auction);
        queue.recupera();
        queue.chiusura(evento());
        assertThat(count()).isZero();
    }

    @Test
    void concurrentWorkersCannotSendSameQueueRowTwice() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timeout worker");
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        // Isola la selezione senza cancellare o modificare notifiche di altre suite.
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.query("SELECT asta_id FROM notifiche_email WHERE asta_id <> ? FOR UPDATE",
                    (rs, row) -> rs.getLong(1), auction);
            var executor = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> first = executor.submit(delivery::inviaProssima);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(delivery.inviaProssima()).isFalse();
                release.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(stato()).isEqualTo("SENT");
                verify(mail, times(1)).send(any(SimpleMailMessage.class));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        });
    }

    private void conclude() {
        jdbc.update("UPDATE aste SET stato = 'CHIUSA', vincitore_id = ?, chiusa_at = CURRENT_TIMESTAMP WHERE id = ?", user, auction);
    }
    private EventoOfferte evento() {
        return new EventoOfferte("AUCTION_CLOSED", new StatoOfferteResponse(auction, product, "CHIUSA",
                new BigDecimal("12.50"), user, "e***l", 1, Instant.now(), user, 1, Instant.now()), null, null, 0);
    }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM notifiche_email WHERE asta_id = ?", Integer.class, auction); }
    private int tentativi() { return jdbc.queryForObject("SELECT tentativi FROM notifiche_email WHERE asta_id = ?", Integer.class, auction); }
    private String stato() { return jdbc.queryForObject("SELECT stato FROM notifiche_email WHERE asta_id = ?", String.class, auction); }
}
