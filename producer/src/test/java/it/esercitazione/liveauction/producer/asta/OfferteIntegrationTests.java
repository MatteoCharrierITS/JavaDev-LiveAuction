package it.esercitazione.liveauction.producer.asta;

import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.exceptions.OffertaException;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.responses.OffertaResponse;
import it.esercitazione.liveauction.producer.asta.services.ChiusuraAstaService;
import it.esercitazione.liveauction.producer.asta.services.OffertaService;
import it.esercitazione.liveauction.producer.asta.services.AstaLetturaService;
import it.esercitazione.liveauction.producer.auth.services.EliminazioneUtenteService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false"}, classes = {
        it.esercitazione.liveauction.producer.LiveAuctionProducerApplication.class,
        OfferteIntegrationTests.EventiTestConfig.class
})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class OfferteIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant FINE = NOW.plusSeconds(360);

    @Autowired JdbcTemplate jdbc;
    @Autowired OffertaService offerte;
    @Autowired ChiusuraAstaService chiusura;
    @Autowired AstaLetturaService lettura;
    @Autowired EliminazioneUtenteService eliminazione;
    @Autowired EventiRegistrati eventi;
    @MockitoBean(name = "clockOfferte") Clock clock;

    private long admin;
    private long primo;
    private long secondo;
    private long terzo;
    private long prodotto;
    private long asta;
    private long categoria;
    private final List<Long> utentiCreati = new java.util.ArrayList<>();

    @BeforeEach
    void prepara() {
        when(clock.instant()).thenReturn(NOW);
        admin = utente("ADMIN", "0");
        primo = utente("USER", "1000");
        secondo = utente("USER", "1000");
        terzo = utente("USER", "1000");
        String suffisso = UUID.randomUUID().toString().substring(0, 8);
        categoria = jdbc.queryForObject("INSERT INTO categorie(nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "Categoria " + suffisso, "test-" + suffisso);
        prodotto = jdbc.queryForObject("""
                INSERT INTO prodotti(categoria_id, sku, nome, astabile, quantita_disponibile, quantita_bloccata)
                VALUES (?, ?, 'Prodotto test', TRUE, 2, 1) RETURNING id
                """, Long.class, categoria, "TEST-" + suffisso);
        asta = nuovaAsta(prodotto);
    }

    @AfterEach
    void pulisciSoloFixturePropri() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM movimenti_portafoglio WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id=?)", prodotto);
        jdbc.update("DELETE FROM offerte WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id=?)", prodotto);
        jdbc.update("DELETE FROM aste WHERE prodotto_id=?", prodotto);
        jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id=?", prodotto);
        jdbc.update("DELETE FROM prodotti WHERE id=?", prodotto);
        jdbc.update("DELETE FROM categorie WHERE id=?", categoria);
        for (long id : utentiCreati) {
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id=?", id);
            jdbc.update("DELETE FROM portafogli WHERE utente_id=?", id);
            jdbc.update("DELETE FROM utenti WHERE id=?", id);
        }
    }

    @Test
    void firstBidAtInitialPriceReservesFundsAndExtendsCurrentDeadline() {
        OffertaResponse risposta = offri(primo, asta, "100");
        assertThat(risposta.duplicata()).isFalse();
        assertThat(risposta.stato().fineAt()).isEqualTo(FINE.plusSeconds(20));
        assertThat(risposta.stato().sequence()).isEqualTo(1);
        assertThat(risposta.stato().migliorOfferenteId()).isEqualTo(primo);
        assertThat(risposta.stato().offerenteDisplay()).contains("***");
        saldo(primo, "1000", "100");
        assertThat(movimenti("RISERVA_OFFERTA")).isEqualTo(1);
        assertThat(eventi.perAsta(asta)).hasSize(1).allSatisfy(osservato -> {
            assertThat(osservato.evento().type()).isEqualTo("BID_ACCEPTED");
            assertThat(osservato.sequenceVisibile()).isGreaterThanOrEqualTo(osservato.evento().stato().sequence());
        });
    }

    @Test
    void overbidReleasesPreviousLeaderAndAcceptsStaleSequence() {
        offri(primo, asta, "100");
        OffertaResponse risposta = offri(secondo, asta, "101");
        assertThat(risposta.snapshotRequired()).isTrue();
        assertThat(risposta.stato().fineAt()).isEqualTo(FINE.plusSeconds(40));
        saldo(primo, "1000", "0");
        saldo(secondo, "1000", "101");
        assertThat(movimenti("RILASCIO_OFFERTA")).isEqualTo(1);
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ? AND leader", asta)).isEqualTo(1);
    }

    @Test
    void invalidAmountsFundsAndSelfBidsHaveNoEffects() {
        rifiutata(() -> offri(primo, asta, "99.99"), "OFFERTA_SUPERATA");
        rifiutata(() -> offri(primo, asta, "1000.01"), "SALDO_INSUFFICIENTE");
        offri(primo, asta, "100");
        rifiutata(() -> offri(primo, asta, "101"), "RILANCIO_SU_SE_STESSO");
        rifiutata(() -> offri(secondo, asta, "100.99"), "OFFERTA_SUPERATA");
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", asta)).isEqualTo(1);
        assertThat(eventi.perAsta(asta)).hasSize(1);
        saldo(secondo, "1000", "0");
    }

    @Test
    void exactAvailableBalanceCanBeReserved() {
        offri(primo, asta, "1000");
        saldo(primo, "1000", "1000");
    }

    @Test
    void preliveStartAndDeadlineAreCheckedOnServer() {
        jdbc.update("UPDATE aste SET stato = 'STANZA_APERTA' WHERE id = ?", asta);
        rifiutata(() -> offri(primo, asta, "100"), "ASTA_NON_APERTA");
        jdbc.update("UPDATE aste SET stato = 'APERTA' WHERE id = ?", asta);
        when(clock.instant()).thenReturn(NOW.minusSeconds(61));
        rifiutata(() -> offri(primo, asta, "100"), "ASTA_NON_APERTA");
        when(clock.instant()).thenReturn(FINE);
        rifiutata(() -> offri(primo, asta, "100"), "ASTA_NON_APERTA");
        when(clock.instant()).thenReturn(FINE.minusNanos(1));
        assertThat(offri(primo, asta, "100").stato().fineAt()).isEqualTo(FINE.plusSeconds(20));
    }

    @Test
    void expiredAfterWaitingForWalletLockIsRejected() throws Exception {
        CountDownLatch walletBloccato = new CountDownLatch(1);
        CountDownLatch liberaWallet = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> blocco = executor.submit(() -> {
                try (var connessione = jdbc.getDataSource().getConnection()) {
                    connessione.setAutoCommit(false);
                    try (var statement = connessione.prepareStatement(
                            "SELECT id FROM portafogli WHERE utente_id = ? FOR UPDATE")) {
                        statement.setLong(1, primo);
                        statement.executeQuery().close();
                    }
                    walletBloccato.countDown();
                    assertThat(liberaWallet.await(10, TimeUnit.SECONDS)).isTrue();
                    connessione.commit();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertThat(walletBloccato.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> richiesta = executor.submit(() -> esito(() -> offri(primo, asta, "100")));
            // Attende che PostgreSQL segnali il lock, senza dipendere da un ritardo arbitrario.
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (conta("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() "
                    + "AND wait_event_type = 'Lock' AND query LIKE '%ORDER BY id FOR UPDATE%'") == 0
                    && System.nanoTime() < limite) {
                Thread.onSpinWait();
            }
            try {
                assertThat(conta("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() "
                        + "AND wait_event_type = 'Lock' AND query LIKE '%ORDER BY id FOR UPDATE%'"))
                        .isGreaterThan(0);
                when(clock.instant()).thenReturn(FINE);
            } finally {
                liberaWallet.countDown();
            }
            assertThat(richiesta.get(10, TimeUnit.SECONDS)).isEqualTo("ASTA_NON_APERTA");
            blocco.get(10, TimeUnit.SECONDS);
        }
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", asta)).isZero();
    }

    @Test
    void duplicateCommandAfterDeadlineDoesNotReserveOrExtendAgain() {
        UUID id = UUID.randomUUID();
        OffertaRequest comando = comando(id, "100", 0);
        OffertaResponse prima = come(primo, "USER", () -> offerte.piazza(primo, asta, comando));
        when(clock.instant()).thenReturn(FINE.plusSeconds(30));
        OffertaResponse replica = come(primo, "USER", () -> offerte.piazza(primo, asta, comando));
        assertThat(replica.duplicata()).isTrue();
        assertThat(replica.offertaId()).isEqualTo(prima.offertaId());
        assertThat(replica.stato().fineAt()).isEqualTo(prima.stato().fineAt());
        assertThat(movimenti("RISERVA_OFFERTA")).isEqualTo(1);
        assertThat(eventi.perAsta(asta)).hasSize(1);
    }

    @Test
    void reusedUuidAndFutureSequenceAreRejected() {
        UUID id = UUID.randomUUID();
        come(primo, "USER", () -> offerte.piazza(primo, asta, comando(id, "100", 0)));
        rifiutata(() -> come(secondo, "USER", () -> offerte.piazza(secondo, asta, comando(id, "110", 1))),
                "CLIENT_BID_ID_GIA_UTILIZZATO");
        rifiutata(() -> come(primo, "USER", () -> offerte.piazza(primo, asta, comando(id, "101", 1))),
                "CLIENT_BID_ID_GIA_UTILIZZATO");
        rifiutata(() -> come(secondo, "USER", () -> offerte.piazza(secondo, asta,
                comando(UUID.randomUUID(), "110", 2))), "SEQUENCE_NON_AGGIORNATA");
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", asta)).isEqualTo(1);
    }

    @Test
    void authorizationAndValidationProtectServiceEntryPoint() {
        assertThatThrownBy(() -> come(admin, "ADMIN", () -> offerte.piazza(admin, asta,
                comando(UUID.randomUUID(), "100", 0)))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> come(primo, "USER", () -> offerte.piazza(secondo, asta,
                comando(UUID.randomUUID(), "100", 0)))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> offri(primo, asta, "100.001")).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> offri(primo, asta, "10000000000.00")).isInstanceOf(ConstraintViolationException.class);
        jdbc.update("UPDATE utenti SET attivo = FALSE WHERE id = ?", primo);
        rifiutata(() -> offri(primo, asta, "100"), "OPERAZIONE_NON_CONSENTITA");
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", asta)).isZero();
    }

    @Test
    void concurrentEqualBidsProduceExactlyOneLeaderAndExtension() throws Exception {
        List<String> esiti = simultanei(() -> esito(() -> offri(primo, asta, "100")),
                () -> esito(() -> offri(secondo, asta, "100")));
        assertThat(esiti).containsExactlyInAnyOrder("OK", "OFFERTA_SUPERATA");
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", asta)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT fine_at FROM aste WHERE id = ?", Timestamp.class, asta).toInstant())
                .isEqualTo(FINE.plusSeconds(20));
    }

    @Test
    void concurrentRetriesHaveOneEffect() throws Exception {
        OffertaRequest comando = comando(UUID.randomUUID(), "100", 0);
        List<Boolean> esiti = simultanei(
                () -> come(primo, "USER", () -> offerte.piazza(primo, asta, comando)).duplicata(),
                () -> come(primo, "USER", () -> offerte.piazza(primo, asta, comando)).duplicata());
        assertThat(esiti).containsExactlyInAnyOrder(false, true);
        assertThat(movimenti("RISERVA_OFFERTA")).isEqualTo(1);
    }

    @Test
    void closeTransfersFundsStockAndInventoryOnlyOnce() throws Exception {
        offri(primo, asta, "100");
        assertThat(chiusura.chiudi(asta).conclusa()).isFalse();
        when(clock.instant()).thenReturn(FINE.plusSeconds(20));
        var esiti = simultanei(() -> chiusura.chiudi(asta), () -> chiusura.chiudi(asta));
        assertThat(esiti).allSatisfy(esito -> {
            assertThat(esito.conclusa()).isTrue();
            assertThat(esito.vincitoreId()).isEqualTo(primo);
            assertThat(esito.prezzoFinale()).isEqualByComparingTo("100");
        });
        saldo(primo, "900", "0");
        saldo(admin, "100", "0");
        assertThat(conta("SELECT quantita FROM inventario_utenti WHERE utente_id = ? AND prodotto_id = ?",
                primo, prodotto)).isEqualTo(1);
        assertThat(conta("SELECT quantita_bloccata FROM prodotti WHERE id = ?", prodotto)).isZero();
        assertThat(movimenti("PAGAMENTO_ASTA")).isEqualTo(1);
        assertThat(movimenti("INCASSO_ASTA")).isEqualTo(1);
        assertThat(eventi.perAsta(asta).stream().filter(e -> e.evento().type().equals("AUCTION_CLOSED"))).hasSize(1);
    }

    @Test
    void noBidsReleaseStockAndTerminalStatesDoNotTransferAgain() {
        when(clock.instant()).thenReturn(FINE);
        assertThat(chiusura.chiudi(asta).vincitoreId()).isNull();
        chiusura.chiudi(asta);
        assertThat(conta("SELECT quantita_disponibile FROM prodotti WHERE id = ?", prodotto)).isEqualTo(3);
        assertThat(conta("SELECT COUNT(*) FROM inventario_utenti WHERE prodotto_id = ?", prodotto)).isZero();
        long annullata = nuovaAsta(prodotto);
        jdbc.update("UPDATE aste SET stato = 'ANNULLATA' WHERE id = ?", annullata);
        assertThat(chiusura.chiudi(annullata).conclusa()).isFalse();
    }

    @Test
    void bidAndCloseAtDeadlineCannotBothWin() throws Exception {
        when(clock.instant()).thenReturn(FINE);
        List<String> esiti = simultanei(() -> esito(() -> offri(primo, asta, "100")),
                () -> chiusura.chiudi(asta).stato());
        assertThat(esiti).containsExactlyInAnyOrder("ASTA_NON_APERTA", "CHIUSA");
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", asta)).isZero();
        saldo(primo, "1000", "0");
    }

    @Test
    void settlementFailureRollsBackFundsLedgerAndEvents() {
        offri(primo, asta, "100");
        jdbc.update("UPDATE prodotti SET quantita_bloccata = 0 WHERE id = ?", prodotto);
        when(clock.instant()).thenReturn(FINE.plusSeconds(20));
        assertThatThrownBy(() -> chiusura.chiudi(asta)).isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        saldo(primo, "1000", "100");
        saldo(admin, "0", "0");
        assertThat(movimenti("PAGAMENTO_ASTA")).isZero();
        assertThat(movimenti("INCASSO_ASTA")).isZero();
        assertThat(eventi.perAsta(asta)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT stato FROM aste WHERE id = ?", String.class, asta)).isEqualTo("APERTA");
    }

    @Test
    void adminBalanceOverflowRollsBackWinnerDebit() {
        offri(primo, asta, "100");
        jdbc.update("UPDATE portafogli SET saldo_totale = 9999999999.99 WHERE utente_id = ?", admin);
        when(clock.instant()).thenReturn(FINE.plusSeconds(20));
        rifiutata(() -> chiusura.chiudi(asta), "LIMITE_SALDO_SUPERATO");
        saldo(primo, "1000", "100");
        assertThat(movimenti("PAGAMENTO_ASTA")).isZero();
    }

    @Test
    void deletingLeaderRestoresBestFundedPreviousOfferWithoutExtendingTimer() {
        offri(primo, asta, "100");
        offri(secondo, asta, "110");
        offri(terzo, asta, "120");
        jdbc.update("UPDATE portafogli SET saldo_totale = 50 WHERE utente_id = ?", secondo);
        elimina(terzo);
        assertThat(lettura.snapshot(asta).migliorOfferente().id()).isEqualTo(primo);
        assertThat(lettura.snapshot(asta).offertaCorrente()).isEqualByComparingTo("100");
        saldo(terzo, "1000", "0");
        saldo(primo, "1000", "100");
        saldo(secondo, "50", "0");
        assertThat(conta("SELECT offerente_id FROM offerte WHERE asta_id = ? AND leader", asta)).isEqualTo(primo);
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ? AND ritirata_at IS NOT NULL", asta))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT fine_at FROM aste WHERE id = ?", Timestamp.class, asta).toInstant())
                .isEqualTo(FINE.plusSeconds(60));
        when(clock.instant()).thenReturn(FINE.plusSeconds(60));
        assertThat(chiusura.chiudi(asta).vincitoreId()).isEqualTo(primo);
    }

    @Test
    void deletingOnlyBidderClearsLeaderAndAllowsNewInitialPriceBid() {
        offri(primo, asta, "100");
        elimina(primo);
        assertThat(jdbc.queryForObject("SELECT offerta_corrente FROM aste WHERE id = ?", BigDecimal.class, asta))
                .isNull();
        assertThat(lettura.snapshot(asta).migliorOfferente()).isNull();
        assertThat(offri(secondo, asta, "100").stato().migliorOfferenteId()).isEqualTo(secondo);
        saldo(primo, "1000", "0");
    }

    @Test
    void deletingFormerBidderLeavesCurrentReservationAndClosedHistoryIntact() {
        offri(primo, asta, "100");
        offri(secondo, asta, "110");
        elimina(primo);
        saldo(secondo, "1000", "110");
        when(clock.instant()).thenReturn(FINE.plusSeconds(40));
        chiusura.chiudi(asta);
        elimina(secondo);
        assertThat(conta("SELECT vincitore_id FROM aste WHERE id = ?", asta)).isEqualTo(secondo);
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ? AND offerente_id = ? "
                + "AND ritirata_at IS NULL", asta, secondo)).isEqualTo(1);
    }

    @Test
    void restoredFundsAreSharedAcrossAllAffectedAuctions() {
        long altra = nuovaAsta(prodotto);
        jdbc.update("UPDATE portafogli SET saldo_totale = 2000 WHERE utente_id = ?", secondo);
        jdbc.update("UPDATE prodotti SET quantita_bloccata = 2, quantita_disponibile = 1 WHERE id = ?", prodotto);
        offri(primo, asta, "600");
        offri(secondo, asta, "700");
        offri(primo, altra, "600");
        offri(secondo, altra, "700");
        elimina(secondo);
        saldo(secondo, "2000", "0");
        saldo(primo, "1000", "600");
        assertThat(conta("SELECT offerente_id FROM offerte WHERE asta_id = ? AND leader", asta)).isEqualTo(primo);
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ? AND leader", altra)).isZero();
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE offerente_id = ? AND ritirata_at IS NOT NULL", secondo))
                .isEqualTo(2);
    }

    @Test
    void simultaneousAuctionsCannotReserveMoreThanAvailableBalance() throws Exception {
        long altra = nuovaAsta(prodotto);
        List<String> esiti = simultanei(() -> esito(() -> offri(primo, asta, "600")),
                () -> esito(() -> offri(primo, altra, "600")));
        assertThat(esiti).containsExactlyInAnyOrder("OK", "SALDO_INSUFFICIENTE");
        saldo(primo, "1000", "600");
    }

    @Test
    void oppositeOverbidsOnTwoAuctionsUseConsistentWalletLockOrder() throws Exception {
        long altra = nuovaAsta(prodotto);
        offri(primo, asta, "100");
        offri(secondo, altra, "100");
        List<String> esiti = simultanei(() -> esito(() -> offri(secondo, asta, "110")),
                () -> esito(() -> offri(primo, altra, "110")));
        assertThat(esiti).containsOnly("OK");
        saldo(primo, "1000", "110");
        saldo(secondo, "1000", "110");
    }

    @Test
    void deletionAndNewBidSerializeWithoutLeavingDeletedUserAsLeader() throws Exception {
        var esiti = simultanei(() -> esito(() -> offri(primo, asta, "100")), () -> {
            elimina(primo);
            return "DELETED";
        });
        assertThat(esiti).contains("DELETED");
        assertThat(esiti).anyMatch(esito -> esito.equals("OK") || esito.equals("OPERAZIONE_NON_CONSENTITA"));
        saldo(primo, "1000", "0");
        assertThat(conta("SELECT COUNT(*) FROM offerte WHERE asta_id = ? AND leader", asta)).isZero();
    }

    @Test
    void deletionAndSettlementSerializeWithoutForeignKeyDeadlock() throws Exception {
        offri(primo, asta, "100");
        when(clock.instant()).thenReturn(FINE.plusSeconds(20));
        simultanei(() -> {
            elimina(primo);
            return "DELETED";
        }, () -> chiusura.chiudi(asta).stato());
        Long vincitore = jdbc.queryForObject("SELECT vincitore_id FROM aste WHERE id = ?", Long.class, asta);
        assertThat(jdbc.queryForObject("SELECT attivo FROM utenti WHERE id = ?", Boolean.class, primo)).isFalse();
        assertThat(jdbc.queryForObject("SELECT stato FROM aste WHERE id = ?", String.class, asta)).isEqualTo("CHIUSA");
        if (vincitore == null) {
            saldo(primo, "1000", "0");
            saldo(admin, "0", "0");
            assertThat(conta("SELECT COUNT(*) FROM inventario_utenti WHERE utente_id = ?", primo)).isZero();
        } else {
            assertThat(vincitore).isEqualTo(primo);
            saldo(primo, "900", "0");
            saldo(admin, "100", "0");
            assertThat(conta("SELECT quantita FROM inventario_utenti WHERE utente_id = ? AND prodotto_id = ?",
                    primo, prodotto)).isEqualTo(1);
        }
    }

    private long utente(String ruolo, String saldo) {
        String username = "test_" + UUID.randomUUID().toString().substring(0, 8);
        long id = jdbc.queryForObject("""
                INSERT INTO utenti(username, email, password_hash, ruolo) VALUES (?, ?, 'test-fixture', ?) RETURNING id
                """, Long.class, username, username + "@example.com", ruolo);
        utentiCreati.add(id);
        jdbc.update("INSERT INTO portafogli(utente_id, saldo_totale) VALUES (?, ?)", id, new BigDecimal(saldo));
        return id;
    }

    private long nuovaAsta(long prodottoId) {
        return jdbc.queryForObject("""
                INSERT INTO aste(prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at)
                VALUES (?, ?, 'APERTA', 100, ?, ?) RETURNING id
                """, Long.class, prodottoId, admin, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(FINE));
    }

    private OffertaResponse offri(long utente, long astaId, String importo) {
        return come(utente, "USER", () -> offerte.piazza(utente, astaId, comando(UUID.randomUUID(), importo, 0)));
    }

    private static OffertaRequest comando(UUID id, String importo, long sequence) {
        return new OffertaRequest("PLACE_BID", id, new BigDecimal(importo), sequence);
    }

    private void elimina(long utente) {
        come(utente, "USER", () -> {
            eliminazione.elimina(utente);
            return null;
        });
    }

    private void saldo(long utente, String totale, String riservato) {
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id = ?", BigDecimal.class, utente))
                .isEqualByComparingTo(totale);
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?", BigDecimal.class, utente))
                .isEqualByComparingTo(riservato);
    }

    private long movimenti(String tipo) {
        return conta("SELECT COUNT(*) FROM movimenti_portafoglio WHERE asta_id = ? AND tipo = ?", asta, tipo);
    }

    private long conta(String sql, Object... parametri) {
        return jdbc.queryForObject(sql, Long.class, parametri);
    }

    private static <T> T come(long utente, String ruolo, Supplier<T> azione) {
        var precedente = SecurityContextHolder.getContext();
        var contesto = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(Long.toString(utente)).build();
        contesto.setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + ruolo))));
        SecurityContextHolder.setContext(contesto);
        try {
            return azione.get();
        } finally {
            SecurityContextHolder.setContext(precedente);
        }
    }

    private static String esito(Supplier<?> azione) {
        try {
            azione.get();
            return "OK";
        } catch (OffertaException exception) {
            return (String) exception.getBody().getProperties().get("code");
        }
    }

    private static void rifiutata(Supplier<?> azione, String code) {
        assertThatThrownBy(azione::get).isInstanceOfSatisfying(OffertaException.class,
                exception -> assertThat(exception.getBody().getProperties()).containsEntry("code", code));
    }

    private static <T> List<T> simultanei(Supplier<T> prima, Supplier<T> seconda) throws Exception {
        CountDownLatch pronti = new CountDownLatch(2);
        CountDownLatch via = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<T> uno = executor.submit(() -> partenza(pronti, via, prima));
            Future<T> due = executor.submit(() -> partenza(pronti, via, seconda));
            assertThat(pronti.await(10, TimeUnit.SECONDS)).isTrue();
            via.countDown();
            return List.of(uno.get(15, TimeUnit.SECONDS), due.get(15, TimeUnit.SECONDS));
        }
    }

    private static <T> T partenza(CountDownLatch pronti, CountDownLatch via, Supplier<T> azione) throws Exception {
        pronti.countDown();
        if (!via.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Avvio concorrente non completato");
        }
        return azione.get();
    }

    @TestConfiguration
    static class EventiTestConfig {
        @Bean EventiRegistrati eventiRegistrati(DataSource datasource) {
            return new EventiRegistrati(datasource);
        }
    }

    static class EventiRegistrati {
        private final DataSource datasource;
        private final List<Osservato> ricevuti = new CopyOnWriteArrayList<>();

        EventiRegistrati(DataSource datasource) {
            this.datasource = datasource;
        }

        @EventListener
        public void ricevi(EventoOfferte evento) throws Exception {
            // Una connessione distinta prova che i dati sono già visibili dopo il commit.
            try (var connessione = datasource.getConnection();
                 var statement = connessione.prepareStatement("SELECT sequence FROM aste WHERE id = ?")) {
                statement.setLong(1, evento.stato().auctionId());
                try (var rs = statement.executeQuery()) {
                    rs.next();
                    ricevuti.add(new Osservato(evento, rs.getLong(1)));
                }
            }
        }

        List<Osservato> perAsta(long id) {
            return ricevuti.stream().filter(evento -> evento.evento().stato().auctionId() == id).toList();
        }
    }

    record Osservato(EventoOfferte evento, long sequenceVisibile) {}
}
