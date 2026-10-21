package it.esercitazione.liveauction.producer.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {
    private static final String PROBLEM_JSON = "application/problem+json";

    private static final List<Tag> TAGS = List.of(

                        tag("Autenticazione", "Registrazione, login, rinnovo dei token e logout. Rotte pubbliche tranne il logout."),
                        tag("Account", "Gestione del proprio account: disattivazione e anonimizzazione."),
                        tag("Catalogo", "Categorie e prodotti pubblici: solo elementi attivi, senza autenticazione."),
                        tag("Catalogo ADMIN", "Gestione di categorie e prodotti, anche non pubblici. Richiede il ruolo ADMIN."),
                        tag("Aste", "Lobby filtrabile e snapshot autorevole delle aste. Rotte pubbliche."),
                        tag("Aste ADMIN", "Programmazione, annullamento e storico delle aste. Richiede il ruolo ADMIN."),
                        tag("Vittorie", "Aste vinte dall'utente autenticato. Richiede il ruolo USER."),
                        tag("Portafoglio", "Saldo in crediti e movimenti dell'utente, in sola lettura. Richiede il ruolo USER."),
                        tag("Inventario", "Prodotti posseduti dall'utente autenticato. Richiede il ruolo USER."),
                        tag("Acquisti", "Acquisto a prezzo fisso, idempotente tramite UUID. Richiede il ruolo USER."),
                        tag("Stanza live", "Ticket monouso per entrare nella stanza WebSocket/STOMP (`/ws`) di un'asta."),
                        tag("Monitoring", "Probe pubbliche di liveness e readiness."));

    @Bean
    OpenAPI liveAuctionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LiveAuction Producer API")
                        .version("v1")
                        .description("""
                                API REST del Producer di **LiveAuction**, il progetto didattico di aste online \
                                in tempo reale: catalogo prodotti, aste programmate dagli amministratori, \
                                portafoglio in crediti (CRD), acquisti a prezzo fisso e inventario personale.

                                **Come autenticarsi.** Le rotte pubbliche (catalogo, lobby e snapshot delle aste, \
                                registrazione, login) non richiedono credenziali. Per le altre:
                                1. crea un utente con `POST /api/v1/auth/register` (parte con il credito iniziale configurato) \
                                oppure usa un account esistente;
                                2. esegui `POST /api/v1/auth/login` e copia il campo `accessToken` della risposta;
                                3. premi **Authorize** in alto a destra, incolla il token (senza il prefisso `Bearer`) \
                                e conferma: Swagger lo invia in `Authorization: Bearer <accessToken>`.

                                L'access token dura 30 minuti; `POST /api/v1/auth/refresh` con il `refreshToken` \
                                (7 giorni) ne emette uno nuovo. Le rotte `/api/v1/admin/**` richiedono il ruolo `ADMIN`, \
                                `/api/v1/me/**` il ruolo `USER`.

                                **Errori.** Tutti gli errori sono `application/problem+json` e, dove il contratto \
                                lo prevede, contengono un `code` applicativo (per esempio `RISORSA_NON_TROVATA`).

                                **Stanza live.** La stanza d'asta in tempo reale usa STOMP su WebSocket all'endpoint `/ws`, \
                                con un ticket monouso ottenuto da `POST /api/v1/aste/{id}/ticket`. Il protocollo non è \
                                rappresentabile in OpenAPI ed è descritto in `docs/07-liveauction.md` e in `docs/04-api-rest.md`.""")
                )
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token JWT ottenuto da `POST /api/v1/auth/login` (campo `accessToken`).")));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    @Bean
    OpenApiCustomizer operationSecurity() {
        return api -> {
            api.setTags(new java.util.ArrayList<>(TAGS));
            Schema<?> problem = problemSchema(api);
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean publicGet = method == PathItem.HttpMethod.GET && (
                        path.equals("/api/v1/health") || path.equals("/api/v1/ready")
                                || path.equals("/api/v1/categorie")
                                || path.equals("/api/v1/prodotti") || path.startsWith("/api/v1/prodotti/")
                                || path.equals("/api/v1/aste") || path.startsWith("/api/v1/aste/"));
                boolean publicAuth = method == PathItem.HttpMethod.POST && List.of(
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").contains(path);
                boolean secured = !(publicGet || publicAuth);
                operation.setSecurity(secured ? List.of(new SecurityRequirement().addList("bearerAuth")) : List.of());
                addCommonErrors(path, operation, secured);
                problemContentForErrors(operation, problem);
            }));
        };
    }

    /** Aggiunge 401/403/500 comuni, senza sovrascrivere quelli descritti sul singolo endpoint. */
    private static void addCommonErrors(String path, Operation operation, boolean secured) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        if (secured) {
            responses.putIfAbsent("401", new ApiResponse().description(
                    "Token assente, scaduto o non valido, sessione revocata o account disattivato."));
            boolean admin = path.startsWith("/api/v1/admin/");
            boolean user = path.startsWith("/api/v1/me/") || path.endsWith("/ticket") || path.endsWith("/acquisti");
            if (admin || user) {
                responses.putIfAbsent("403", new ApiResponse().description(
                        admin ? "Ruolo diverso da ADMIN." : "Ruolo diverso da USER."));
            }
        }
        responses.putIfAbsent("500", new ApiResponse().description("Errore interno imprevisto."));
    }

    /** Gli errori (4xx/5xx) senza un corpo descritto usano `application/problem+json` con `ProblemDetail`. */
    private static void problemContentForErrors(Operation operation, Schema<?> problem) {
        operation.getResponses().forEach((code, response) -> {
            boolean error = code.length() == 3 && (code.startsWith("4") || code.startsWith("5"));
            if (error && (response.getContent() == null || response.getContent().isEmpty())) {
                response.setContent(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(problem)));
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private static Schema<?> problemSchema(OpenAPI api) {
        if (api.getComponents() == null) {
            api.setComponents(new Components());
        }
        Map<String, Schema> known = ModelConverters.getInstance().readAll(ProblemDetail.class);
        known.forEach((name, schema) -> {
            if (api.getComponents().getSchemas() == null || !api.getComponents().getSchemas().containsKey(name)) {
                api.getComponents().addSchemas(name, schema);
            }
            if ("ProblemDetail".equals(name) && schema.getDescription() == null) {
                schema.setDescription("Errore REST (`application/problem+json`): `status`, `title`, `detail` e, dove previsto, `code` applicativo.");
            }
        });
        return new Schema<>().$ref("#/components/schemas/ProblemDetail");
    }
}
