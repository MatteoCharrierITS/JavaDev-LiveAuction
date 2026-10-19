package it.esercitazione.liveauction.producer.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.acquisto.AcquistiProperties;
import it.esercitazione.liveauction.producer.asta.requests.ProgrammaAstaRequest;
import it.esercitazione.liveauction.producer.asta.services.AstaService;
import it.esercitazione.liveauction.producer.auth.models.Ruolo;
import it.esercitazione.liveauction.producer.auth.repos.UtenteRepository;
import it.esercitazione.liveauction.producer.auth.requests.RegistrazioneRequest;
import it.esercitazione.liveauction.producer.auth.responses.RegistrazioneResponse;
import it.esercitazione.liveauction.producer.auth.services.RegistrazioneService;
import it.esercitazione.liveauction.producer.demo.DemoCatalogo.DemoProdotto;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.prodotto.models.Categoria;
import it.esercitazione.liveauction.producer.prodotto.repos.CategoriaRepository;
import it.esercitazione.liveauction.producer.prodotto.repos.ProdottoRepository;
import it.esercitazione.liveauction.producer.prodotto.requests.CategoriaRequest;
import it.esercitazione.liveauction.producer.prodotto.requests.NuovoProdottoRequest;
import it.esercitazione.liveauction.producer.prodotto.services.CategoriaService;
import it.esercitazione.liveauction.producer.prodotto.services.ProdottoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dati demo all'avvio (attivo solo con {@code app.demo.seed=true}). Idempotente: crea soltanto ciò che manca
 * e usa i servizi di dominio (registrazione, catalogo, programmazione aste) e il ledger del portafoglio.
 * Qualunque errore viene registrato nel log ma non impedisce l'avvio del Producer.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo", name = "seed", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final ZoneId ZONA_ASTE = ZoneId.of("Europe/Rome");
    private static final BigDecimal SALDO_INIZIALE = new BigDecimal("5000.00");
    /** Minuti dall'avvio a cui partono le aste demo: la prima tra pochi minuti, le altre nelle ore successive. */
    private static final int[] PARTENZE_MINUTI = {2, 45, 120, 240};

    private static final Map<String, Ruolo> UTENTI = new LinkedHashMap<>();

    static {
        UTENTI.put("matteo", Ruolo.ADMIN);
        for (String nome : List.of("andrea", "maikol", "cristian", "marco", "tommi")) {
            UTENTI.put(nome, Ruolo.USER);
        }
    }

    private final UtenteRepository utenti;
    private final RegistrazioneService registrazione;
    private final PortafoglioRepository portafogli;
    private final CategoriaRepository categorie;
    private final ProdottoRepository prodotti;
    private final CategoriaService categoriaService;
    private final ProdottoService prodottoService;
    private final AstaService astaService;
    private final AcquistiProperties acquisti;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transazione;
    private final ObjectMapper mapper;
    private final String password;
    private final String catalogUrl;

    public DemoSeeder(UtenteRepository utenti, RegistrazioneService registrazione, PortafoglioRepository portafogli,
                      CategoriaRepository categorie, ProdottoRepository prodotti, CategoriaService categoriaService,
                      ProdottoService prodottoService, AstaService astaService, AcquistiProperties acquisti,
                      JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ObjectMapper mapper,
                      @Value("${app.demo.password:Demo2026!}") String password,
                      @Value("${app.demo.catalog-url:https://dummyjson.com/products?limit=40&select=title,description,category,price,sku,thumbnail,stock}") String catalogUrl) {
        this.utenti = utenti;
        this.registrazione = registrazione;
        this.portafogli = portafogli;
        this.categorie = categorie;
        this.prodotti = prodotti;
        this.categoriaService = categoriaService;
        this.prodottoService = prodottoService;
        this.astaService = astaService;
        this.acquisti = acquisti;
        this.jdbc = jdbc;
        this.transazione = new TransactionTemplate(transactionManager);
        this.mapper = mapper;
        this.password = password;
        this.catalogUrl = catalogUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException exception) {
            log.error("Dati demo: popolamento interrotto, il Producer continua l'avvio", exception);
        }
    }

    void seed() {
        log.info("Dati demo: avvio del popolamento idempotente");
        creaUtenti();
        Long adminId = trovaAdmin();
        if (adminId == null) {
            log.warn("Dati demo: nessun ADMIN attivo, catalogo e aste demo non creati");
            return;
        }
        assicuraPortafoglio(adminId);
        if (acquisti.getAdminId() <= 0) {
            acquisti.setAdminId(adminId);
            log.info("Dati demo: SHOP_ADMIN_ID non impostato, gli acquisti fissi incassano sull'ADMIN {}", adminId);
        }
        comeAdmin(adminId, () -> {
            creaCatalogo();
            programmaAste(adminId);
        });
        log.info("Dati demo: popolamento terminato");
    }

    private void creaUtenti() {
        UTENTI.forEach((username, ruolo) -> {
            try {
                if (!utenti.existsByUsername(username)) {
                    RegistrazioneResponse creato = registrazione.registra(new RegistrazioneRequest(
                            username, username + "@demo.local", password));
                    if (ruolo == Ruolo.ADMIN) {
                        utenti.findById(creato.id()).ifPresent(utente -> {
                            utente.setRuolo(Ruolo.ADMIN);
                            utenti.save(utente);
                        });
                    }
                    log.info("Dati demo: creato l'utente {} ({})", username, ruolo);
                }
                utenti.findByUsername(username)
                        .filter(utente -> utente.getRuolo() == Ruolo.USER && utente.isAttivo())
                        .ifPresent(utente -> ricarica(utente.getId(), username));
            } catch (RuntimeException exception) {
                log.warn("Dati demo: utente {} non creato ({})", username, exception.toString());
            }
        });
    }

    /** Ricarica una sola volta: solo se il portafoglio non ha alcun movimento di ledger. */
    private void ricarica(long utenteId, String username) {
        transazione.executeWithoutResult(stato -> {
            var saldo = portafogli.bloccaPortafogli(List.of(utenteId)).get(utenteId);
            if (portafogli.contaMovimenti(saldo.id()) == 0) {
                portafogli.movimenta(saldo, null, TipoMovimento.IMPOSTAZIONE_SALDO, SALDO_INIZIALE,
                        SALDO_INIZIALE.subtract(saldo.totale()), BigDecimal.ZERO,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
                log.info("Dati demo: portafoglio di {} ricaricato a {} CRD", username, SALDO_INIZIALE);
            }
        });
    }

    /** Il conto dell'ADMIN deve esistere per incassare aste e acquisti fissi. */
    private void assicuraPortafoglio(long adminId) {
        jdbc.update("INSERT INTO portafogli (utente_id) VALUES (?) ON CONFLICT (utente_id) DO NOTHING", adminId);
    }

    private Long trovaAdmin() {
        return utenti.findByUsername("matteo")
                .filter(utente -> utente.getRuolo() == Ruolo.ADMIN && utente.isAttivo())
                .map(utente -> utente.getId())
                .orElseGet(() -> jdbc.query("SELECT id FROM utenti WHERE ruolo = 'ADMIN' AND attivo ORDER BY id LIMIT 1",
                        (rs, row) -> rs.getLong(1)).stream().findFirst().orElse(null));
    }

    private void creaCatalogo() {
        List<DemoProdotto> catalogo = DemoCatalogo.carica(catalogUrl, mapper);
        Map<String, Long> idCategorie = new HashMap<>();
        for (Categoria categoria : categorie.findAll()) {
            idCategorie.put(categoria.getSlug(), categoria.getId());
        }
        Map<String, Integer> contatoreCategoria = new HashMap<>();
        int creati = 0;
        for (DemoProdotto demo : catalogo) {
            int indice = contatoreCategoria.merge(demo.categoriaSlug(), 1, Integer::sum) - 1;
            try {
                if (prodotti.existsBySku(demo.sku())) {
                    continue;
                }
                Long categoriaId = idCategorie.get(demo.categoriaSlug());
                if (categoriaId == null) {
                    categoriaId = categoriaService.crea(
                            new CategoriaRequest(demo.categoriaNome(), demo.categoriaSlug(), true)).id();
                    idCategorie.put(demo.categoriaSlug(), categoriaId);
                }
                boolean astabile = astabile(demo, indice);
                int stock = astabile ? Math.min(Math.max(demo.stock(), 3), 8) : Math.min(Math.max(demo.stock(), 1), 50);
                prodottoService.crea(new NuovoProdottoRequest(categoriaId, demo.sku(), demo.nome(),
                        demo.descrizione(), demo.immagineUrl(), demo.prezzoCrd(), astabile, stock));
                creati++;
            } catch (RuntimeException exception) {
                log.warn("Dati demo: prodotto {} non creato ({})", demo.sku(), exception.toString());
            }
        }
        log.info("Dati demo: {} prodotti creati ({} nella sorgente)", creati, catalogo.size());
    }

    /** Arredamento e profumi sempre all'asta; bellezza uno su due; alimentari uno su cinque. */
    private static boolean astabile(DemoProdotto demo, int indiceNellaCategoria) {
        return switch (demo.categoriaSlug()) {
            case "furniture", "fragrances" -> true;
            case "beauty" -> indiceNellaCategoria % 2 == 0;
            default -> indiceNellaCategoria % 5 == 0;
        };
    }

    /** Aste demo solo se non ce ne sono di programmate o in corso (quelle concluse non contano). */
    private void programmaAste(long adminId) {
        Integer vive = jdbc.queryForObject(
                "SELECT count(*) FROM aste WHERE stato IN ('PROGRAMMATA', 'STANZA_APERTA', 'APERTA')", Integer.class);
        if (vive != null && vive > 0) {
            log.info("Dati demo: ci sono già {} aste programmate o in corso, nessuna asta demo aggiunta", vive);
            return;
        }
        record Candidato(long id, String categoria, BigDecimal prezzo) {
        }
        List<Candidato> candidati = jdbc.query("""
                SELECT p.id, c.slug, COALESCE(p.prezzo_fisso, 100) AS prezzo
                FROM prodotti p JOIN categorie c ON c.id = p.categoria_id
                WHERE p.astabile AND p.attivo AND c.attiva AND p.quantita_disponibile > 0
                ORDER BY p.prezzo_fisso DESC NULLS LAST, p.id
                """, (rs, row) -> new Candidato(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3)));
        // Una per categoria finché possibile, poi i restanti.
        List<Candidato> scelti = new ArrayList<>();
        Map<String, Boolean> viste = new HashMap<>();
        for (Candidato candidato : candidati) {
            if (scelti.size() < PARTENZE_MINUTI.length && viste.putIfAbsent(candidato.categoria(), true) == null) {
                scelti.add(candidato);
            }
        }
        for (Candidato candidato : candidati) {
            if (scelti.size() < PARTENZE_MINUTI.length && !scelti.contains(candidato)) {
                scelti.add(candidato);
            }
        }
        LocalDateTime adesso = LocalDateTime.now(ZONA_ASTE).withSecond(0).withNano(0);
        for (int i = 0; i < scelti.size(); i++) {
            Candidato candidato = scelti.get(i);
            // Base d'asta al 15% del prezzo fisso, arrotondata a 5 CRD, tra 10 e 600.
            BigDecimal base = candidato.prezzo().multiply(new BigDecimal("0.15"))
                    .divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(5))
                    .max(BigDecimal.TEN).min(BigDecimal.valueOf(600)).setScale(2);
            try {
                var asta = astaService.programmaAsta(adminId, new ProgrammaAstaRequest(candidato.id(),
                        adesso.plusMinutes(PARTENZE_MINUTI[i]), "Europe/Rome", base));
                log.info("Dati demo: asta {} programmata per {} (prodotto {}, base {} CRD)",
                        asta.id(), asta.inizioAt(), candidato.id(), base);
            } catch (RuntimeException exception) {
                log.warn("Dati demo: asta per il prodotto {} non programmata ({})", candidato.id(), exception.toString());
            }
        }
    }

    /** I servizi di dominio sono protetti da {@code @PreAuthorize}: il seeder agisce come l'ADMIN scelto. */
    private static void comeAdmin(long adminId, Runnable operazione) {
        Instant ora = Instant.now();
        Jwt jwt = Jwt.withTokenValue("demo-seed").header("alg", "none").subject(Long.toString(adminId))
                .issuedAt(ora).expiresAt(ora.plusSeconds(600)).build();
        SecurityContext contesto = SecurityContextHolder.createEmptyContext();
        contesto.setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "demo-seed"));
        SecurityContext precedente = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(contesto);
        try {
            operazione.run();
        } finally {
            SecurityContextHolder.setContext(precedente);
        }
    }
}
