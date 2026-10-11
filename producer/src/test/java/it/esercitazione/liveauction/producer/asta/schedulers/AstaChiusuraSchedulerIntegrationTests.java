package it.esercitazione.liveauction.producer.asta.schedulers;

import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
import it.esercitazione.liveauction.producer.asta.services.ChiusuraAstaService;
import it.esercitazione.liveauction.producer.asta.services.OffertaService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "spring.datasource.url=${DB_URL}"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@Import(AstaChiusuraSchedulerIntegrationTests.EventConfig.class)
class AstaChiusuraSchedulerIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AstaRepository repository;
    @Autowired AstaLifecycleService lifecycle;
    @Autowired ChiusuraAstaService chiusura;
    @Autowired OffertaService offerte;
    @Autowired Eventi eventi;
    @MockitoBean(name = "clockOfferte") Clock clock;

    private final List<Long> prodotti = new ArrayList<>();
    private final List<Long> utenti = new ArrayList<>();
    private long categoria;
    private long admin;
    private long user;
    private Instant now;

    @BeforeEach
    void prepara() {
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(clock.instant()).thenReturn(now);
        eventi.valori.clear();
        String tag = UUID.randomUUID().toString();
        categoria = jdbc.queryForObject("INSERT INTO categorie(nome,slug) VALUES (?,?) RETURNING id",
                Long.class, tag, tag);
        admin = utente("ADMIN", 0);
        user = utente("USER", 1000);
    }

    @AfterEach
    void pulisciSoloFixturePropri() {
        SecurityContextHolder.clearContext();
        for (long p : prodotti) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id=?)", p);
            jdbc.update("DELETE FROM offerte WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id=?)", p);
            jdbc.update("DELETE FROM aste WHERE prodotto_id=?", p); // anche la coda email, ON DELETE CASCADE
            jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id=?", p);
            jdbc.update("DELETE FROM prodotti WHERE id=?", p);
        }
        jdbc.update("DELETE FROM categorie WHERE id=?", categoria);
        for (long u : utenti) {
            jdbc.update("DELETE FROM portafogli WHERE utente_id=?", u);
            jdbc.update("DELETE FROM utenti WHERE id=?", u);
        }
    }

    @Test
    void cicloPeriodicoChiudeConVincitoreUnaSolaVoltaEAccodaEmail() {
        long p = prodotto(1);
        long a = asta(p, "APERTA", now.minusSeconds(60));
        offri(a);
        when(clock.instant()).thenReturn(now.plusSeconds(380));
        scheduler().aggiornaAste();
        scheduler().aggiornaAste();
        verificaVittoria(a, p);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifiche_email WHERE asta_id=?", Integer.class, a))
                .isEqualTo(1);
    }

    @Test
    void riavvioRecuperaProgrammataScadutaERestituisceStockSenzaOfferte() {
        long p = prodotto(1);
        long a = asta(p, "PROGRAMMATA", now.minusSeconds(900));
        scheduler().recuperaAllAvvio();
        scheduler().aggiornaAste();
        assertThat(stato(a)).isEqualTo("CHIUSA");
        assertThat(jdbc.queryForObject("SELECT fine_at FROM aste WHERE id=?", Timestamp.class, a).toInstant())
                .isEqualTo(now.minusSeconds(480));
        assertThat(jdbc.queryForObject("SELECT sequence FROM aste WHERE id=?", Long.class, a)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT quantita_disponibile FROM prodotti WHERE id=?", Integer.class, p))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventario_utenti WHERE prodotto_id=?", Integer.class, p))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimenti_portafoglio WHERE asta_id=?", Integer.class, a))
                .isZero();
        assertThat(eventi.chiusure(a)).isEqualTo(1);
    }

    @Test
    void riavvioChiudeAncheApertaScadutaConVincitore() {
        long p = prodotto(1);
        long a = asta(p, "APERTA", now.minusSeconds(60));
        offri(a);
        when(clock.instant()).thenReturn(now.plusSeconds(380));
        scheduler().recuperaAllAvvio();
        verificaVittoria(a, p);
    }

    @Test
    void rispettaEstensioneDaRilancioFinoAllaNuovaScadenza() {
        long p = prodotto(1);
        long a = asta(p, "APERTA", now.minusSeconds(60));
        offri(a);
        when(clock.instant()).thenReturn(now.plusSeconds(360));
        scheduler().aggiornaAste();
        assertThat(stato(a)).isEqualTo("APERTA");
        assertThat(eventi.chiusure(a)).isZero();
        when(clock.instant()).thenReturn(now.plusSeconds(380));
        scheduler().aggiornaAste();
        verificaVittoria(a, p);
    }

    @Test
    void erroreSuUnaChiusuraFaRollbackNonBloccaLeAltreERitenta() {
        long p = prodotto(1);
        long fallita = asta(p, "APERTA", now.minusSeconds(60));
        offri(fallita);
        jdbc.update("UPDATE prodotti SET quantita_bloccata=0 WHERE id=?", p);
        long altra = asta(prodotto(1), "APERTA", now.minusSeconds(900));
        when(clock.instant()).thenReturn(now.plusSeconds(380));
        scheduler().aggiornaAste();
        assertThat(stato(altra)).isEqualTo("CHIUSA");
        assertThat(stato(fallita)).isEqualTo("APERTA");
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id=?", BigDecimal.class, user))
                .isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimenti_portafoglio WHERE asta_id=? AND tipo='PAGAMENTO_ASTA'",
                Integer.class, fallita)).isZero();
        assertThat(eventi.chiusure(fallita)).isZero();
        jdbc.update("UPDATE prodotti SET quantita_bloccata=1 WHERE id=?", p);
        scheduler().aggiornaAste();
        verificaVittoria(fallita, p);
    }

    @Test
    void dueCicliConcorrentiNonDuplicanoTrasferimentiOEventi() throws Exception {
        long p = prodotto(1);
        long a = asta(p, "APERTA", now.minusSeconds(60));
        offri(a);
        when(clock.instant()).thenReturn(now.plusSeconds(380));
        AstaScheduler job = scheduler();
        CountDownLatch pronte = new CountDownLatch(2);
        CountDownLatch partenza = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Runnable ciclo = () -> {
                pronte.countDown();
                try {
                    if (!partenza.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout partenza");
                    job.aggiornaAste();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            };
            var prima = pool.submit(ciclo);
            var seconda = pool.submit(ciclo);
            try {
                assertThat(pronte.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                partenza.countDown();
            }
            prima.get(15, TimeUnit.SECONDS);
            seconda.get(15, TimeUnit.SECONDS);
        }
        verificaVittoria(a, p);
    }

    private AstaScheduler scheduler() {
        return new AstaScheduler(repository, lifecycle, chiusura, Clock.fixed(clock.instant(), ZoneOffset.UTC));
    }

    private void offri(long asta) {
        var jwt = Jwt.withTokenValue("test-not-a-real-token").header("alg", "HS256")
                .subject(Long.toString(user)).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            offerte.piazza(user, asta, new OffertaRequest("PLACE_BID", UUID.randomUUID(), new BigDecimal("100"), 0L));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void verificaVittoria(long a, long p) {
        assertThat(stato(a)).isEqualTo("CHIUSA");
        assertThat(jdbc.queryForObject("SELECT vincitore_id FROM aste WHERE id=?", Long.class, a)).isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id=?", BigDecimal.class, user))
                .isEqualByComparingTo("900");
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id=?", BigDecimal.class, user))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id=?", BigDecimal.class, admin))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT quantita FROM inventario_utenti WHERE utente_id=? AND prodotto_id=?",
                Integer.class, user, p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantita_bloccata FROM prodotti WHERE id=?", Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimenti_portafoglio WHERE asta_id=? AND tipo IN ('PAGAMENTO_ASTA','INCASSO_ASTA')",
                Integer.class, a)).isEqualTo(2);
        assertThat(eventi.chiusure(a)).isEqualTo(1);
    }

    private String stato(long a) { return jdbc.queryForObject("SELECT stato FROM aste WHERE id=?", String.class, a); }

    private long utente(String ruolo, int saldo) {
        String tag = UUID.randomUUID().toString();
        long id = jdbc.queryForObject("INSERT INTO utenti(username,email,password_hash,ruolo) VALUES (?,?,'fixture',?) RETURNING id",
                Long.class, tag, tag + "@example.invalid", ruolo);
        utenti.add(id);
        jdbc.update("INSERT INTO portafogli(utente_id,saldo_totale) VALUES (?,?)", id, saldo);
        return id;
    }

    private long prodotto(int bloccata) {
        long id = jdbc.queryForObject("INSERT INTO prodotti(categoria_id,sku,nome,astabile,quantita_disponibile,quantita_bloccata) VALUES (?,?,'Scheduler test',true,2,?) RETURNING id",
                Long.class, categoria, UUID.randomUUID().toString().substring(0, 20), bloccata);
        prodotti.add(id);
        return id;
    }

    private long asta(long p, String stato, Instant inizio) {
        return jdbc.queryForObject("INSERT INTO aste(prodotto_id,admin_id,stato,prezzo_iniziale,inizio_at,fine_at) VALUES (?,?,?,100,?,?) RETURNING id",
                Long.class, p, admin, stato, Timestamp.from(inizio), Timestamp.from(inizio.plusSeconds(420)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfig {
        @Bean Eventi eventiChiusuraScheduler() { return new Eventi(); }
    }

    static class Eventi {
        final List<EventoOfferte> valori = new CopyOnWriteArrayList<>();
        @EventListener public void ricevi(EventoOfferte e) { valori.add(e); }
        long chiusure(long a) {
            return valori.stream().filter(e -> e.stato().auctionId() == a && e.type().equals("AUCTION_CLOSED")).count();
        }
    }
}
