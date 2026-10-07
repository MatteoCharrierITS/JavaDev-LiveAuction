package it.esercitazione.liveauction.producer.asta;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent.Tipo;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.schedulers.AstaScheduler;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "spring.datasource.url=${DB_URL}"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@Import(AstaLifecycleIntegrationTests.EventConfig.class)
class AstaLifecycleIntegrationTests {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AstaRepository repository;
    @Autowired
    AstaLifecycleService service;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    EventCollector collector;

    private Long categoriaId;
    private Long prodottoId;
    private Long adminId;

    @BeforeEach
    void preparaRecordIsolati() {
        collector.eventi.clear();
        String suffisso = UUID.randomUUID().toString().substring(0, 8);
        categoriaId = jdbc.queryForObject("INSERT INTO categorie (nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "Test ciclo " + suffisso, "test-ciclo-" + suffisso);
        prodottoId = jdbc.queryForObject("""
                INSERT INTO prodotti (categoria_id, sku, nome, astabile, quantita_disponibile)
                VALUES (?, ?, ?, TRUE, 10) RETURNING id
                """, Long.class, categoriaId, "CICLO-" + suffisso, "Prodotto test ciclo");
        adminId = jdbc.queryForObject("""
                INSERT INTO utenti (username, email, password_hash, ruolo)
                VALUES (?, ?, 'test-non-usato', 'ADMIN') RETURNING id
                """, Long.class, "admin_ciclo_" + suffisso, "admin_ciclo_" + suffisso + "@example.invalid");
    }

    @AfterEach
    void pulisciSoloIRecordDelTest() {
        if (prodottoId != null) {
            jdbc.update("DELETE FROM aste WHERE prodotto_id = ?", prodottoId);
            jdbc.update("DELETE FROM prodotti WHERE id = ?", prodottoId);
        }
        if (categoriaId != null) {
            jdbc.update("DELETE FROM categorie WHERE id = ?", categoriaId);
        }
        if (adminId != null) {
            jdbc.update("DELETE FROM utenti WHERE id = ?", adminId);
        }
    }

    @Test
    void apreStanzaSenzaDuplicatiONuovePrenotazioni() {
        Instant inizio = adesso().plusSeconds(120);
        long id = creaAsta(Stato.PROGRAMMATA, inizio, 0);
        assertThat(service.aggiornaStato(id)).isEqualTo(1);
        assertThat(service.aggiornaStato(id)).isZero();
        assertAsta(id, Stato.STANZA_APERTA, 1, inizio.plusSeconds(420));
        assertThat(eventi(id)).extracting(e -> e.evento().type()).containsExactly(Tipo.ROOM_OPENED);
        assertThat(eventi(id)).extracting(EventoOsservato::sequencePersistita).containsExactly(1L);
        assertThat(jdbc.queryForObject("SELECT quantita_bloccata FROM prodotti WHERE id = ?",
                Integer.class, prodottoId)).isEqualTo(1);
    }

    @Test
    void avviaStanzaGiaApertaConScadenzaOriginaria() {
        Instant inizio = adesso().minusSeconds(10);
        long id = creaAsta(Stato.STANZA_APERTA, inizio, 1);
        assertThat(service.aggiornaStato(id)).isEqualTo(1);
        assertThat(service.aggiornaStato(id)).isZero();
        assertAsta(id, Stato.APERTA, 2, inizio.plusSeconds(420));
        assertThat(eventi(id)).extracting(e -> e.evento().type()).containsExactly(Tipo.AUCTION_STARTED);
    }

    @Test
    void recuperaDopoRiavvioEPubblicaSoloDopoCommit() {
        Instant inizio = adesso().minusSeconds(120);
        long id = creaAsta(Stato.PROGRAMMATA, inizio, 0);
        TransactionTemplate transazione = new TransactionTemplate(transactionManager);
        transazione.executeWithoutResult(status -> {
            assertThat(service.aggiornaStato(id)).isEqualTo(2);
            assertThat(eventi(id)).isEmpty();
        });
        assertAsta(id, Stato.APERTA, 2, inizio.plusSeconds(420));
        assertThat(eventi(id)).extracting(e -> e.evento().type())
                .containsExactly(Tipo.ROOM_OPENED, Tipo.AUCTION_STARTED);
        // Il listener legge da una nuova transazione: vede già entrambe le transizioni committate.
        assertThat(eventi(id)).extracting(EventoOsservato::sequencePersistita).containsExactly(2L, 2L);
    }

    @Test
    void rollbackNonPubblicaEventiERendePossibileIlRecupero() {
        Instant inizio = adesso().minusSeconds(120);
        long id = creaAsta(Stato.PROGRAMMATA, inizio, 0);
        TransactionTemplate transazione = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transazione.executeWithoutResult(status -> {
            service.aggiornaStato(id);
            throw new IllegalStateException("rollback simulato");
        })).isInstanceOf(IllegalStateException.class);
        assertAsta(id, Stato.PROGRAMMATA, 0, inizio.plusSeconds(420));
        assertThat(eventi(id)).isEmpty();
        assertThat(service.aggiornaStato(id)).isEqualTo(2);
        assertThat(eventi(id)).hasSize(2);
    }

    @Test
    void dueJobConcorrentiNonDuplicanoStatiOEventi() throws Exception {
        Instant inizio = adesso().minusSeconds(120);
        long id = creaAsta(Stato.PROGRAMMATA, inizio, 0);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch pronte = new CountDownLatch(2);
        CountDownLatch partenza = new CountDownLatch(1);
        try {
            var prima = executor.submit(() -> attivaInConcorrenza(id, pronte, partenza));
            var seconda = executor.submit(() -> attivaInConcorrenza(id, pronte, partenza));
            assertThat(pronte.await(5, TimeUnit.SECONDS)).isTrue();
            partenza.countDown();
            assertThat(List.of(prima.get(10, TimeUnit.SECONDS), seconda.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(2, 0);
            assertAsta(id, Stato.APERTA, 2, inizio.plusSeconds(420));
            assertThat(eventi(id)).extracting(e -> e.evento().type())
                    .containsExactly(Tipo.ROOM_OPENED, Tipo.AUCTION_STARTED);
        } finally {
            partenza.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cicloDiAvvioRecuperaSoloLeAsteDovute() {
        Instant adesso = adesso();
        long stanza = creaAsta(Stato.PROGRAMMATA, adesso.plusSeconds(120), 0);
        long live = creaAsta(Stato.PROGRAMMATA, adesso.minusSeconds(120), 0);
        long preLive = creaAsta(Stato.STANZA_APERTA, adesso.minusSeconds(10), 1);
        long futura = creaAsta(Stato.PROGRAMMATA, adesso.plusSeconds(600), 0);
        long annullata = creaAsta(Stato.ANNULLATA, adesso.minusSeconds(10), 0);
        AstaScheduler scheduler = new AstaScheduler(repository, service);
        scheduler.recuperaAllAvvio();
        scheduler.aggiornaAste();
        assertAsta(stanza, Stato.STANZA_APERTA, 1, adesso.plusSeconds(540));
        assertAsta(live, Stato.APERTA, 2, adesso.plusSeconds(300));
        assertAsta(preLive, Stato.APERTA, 2, adesso.plusSeconds(410));
        assertAsta(futura, Stato.PROGRAMMATA, 0, adesso.plusSeconds(1020));
        assertAsta(annullata, Stato.ANNULLATA, 0, adesso.plusSeconds(410));
        // Il job vede anche i fixture lasciati da altre suite nel database condiviso.
        // Verificare solo le aste di questo test, non il numero globale di eventi.
        assertThat(collector.eventi).filteredOn(e -> e.evento().auctionId() == stanza
                || e.evento().auctionId() == live || e.evento().auctionId() == preLive).hasSize(4);
    }

    @Test
    void recuperoAstaGiaScadutaNonSpostaLaScadenza() {
        Instant inizio = adesso().minusSeconds(900);
        long id = creaAsta(Stato.PROGRAMMATA, inizio, 0);
        assertThat(service.aggiornaStato(id)).isEqualTo(2);
        assertAsta(id, Stato.APERTA, 2, inizio.plusSeconds(420));
        assertThat(inizio.plusSeconds(420)).isBefore(Instant.now());
    }

    private int attivaInConcorrenza(long id, CountDownLatch pronte, CountDownLatch partenza)
            throws InterruptedException {
        pronte.countDown();
        if (!partenza.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Job concorrenti non avviati");
        }
        return service.aggiornaStato(id);
    }

    private long creaAsta(Stato stato, Instant inizio, long sequence) {
        long id = jdbc.queryForObject("""
                INSERT INTO aste (prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at, sequence)
                VALUES (?, ?, ?, 500.00, ?, ?, ?) RETURNING id
                """, Long.class, prodottoId, adminId, stato.name(), Timestamp.from(inizio),
                Timestamp.from(inizio.plusSeconds(420)), sequence);
        if (stato != Stato.CHIUSA && stato != Stato.ANNULLATA) {
            jdbc.update("""
                    UPDATE prodotti SET quantita_disponibile = quantita_disponibile - 1,
                    quantita_bloccata = quantita_bloccata + 1 WHERE id = ?
                    """, prodottoId);
        }
        return id;
    }

    private void assertAsta(long id, Stato stato, long sequence, Instant fine) {
        assertThat(jdbc.queryForObject("SELECT stato FROM aste WHERE id = ?", String.class, id))
                .isEqualTo(stato.name());
        assertThat(jdbc.queryForObject("SELECT sequence FROM aste WHERE id = ?", Long.class, id)).isEqualTo(sequence);
        assertThat(jdbc.queryForObject("SELECT fine_at FROM aste WHERE id = ?", Timestamp.class, id).toInstant())
                .isEqualTo(fine);
    }

    private List<EventoOsservato> eventi(long id) {
        return collector.eventi.stream().filter(e -> e.evento().auctionId() == id).toList();
    }

    private static Instant adesso() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfig {
        @Bean
        EventCollector eventCollector(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
            return new EventCollector(jdbc, transactionManager);
        }
    }

    record EventoOsservato(AstaTransizioneEvent evento, long sequencePersistita) {
    }

    static class EventCollector {
        final List<EventoOsservato> eventi = new CopyOnWriteArrayList<>();
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transazione;

        EventCollector(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
            this.jdbc = jdbc;
            transazione = new TransactionTemplate(transactionManager);
            transazione.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transazione.setReadOnly(true);
        }

        @EventListener
        public void ricevi(AstaTransizioneEvent evento) {
            Long sequence = transazione.execute(status -> jdbc.queryForObject(
                    "SELECT sequence FROM aste WHERE id = ?", Long.class, evento.auctionId()));
            eventi.add(new EventoOsservato(evento, sequence));
        }
    }
}
