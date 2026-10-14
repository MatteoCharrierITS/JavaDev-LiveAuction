package it.esercitazione.liveauction.consumer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.consumer.dto.*;
import it.esercitazione.liveauction.consumer.marketplace.CategoriaView;
import it.esercitazione.liveauction.consumer.marketplace.ProdottoPage;
import it.esercitazione.liveauction.consumer.marketplace.ProdottoView;
import it.esercitazione.liveauction.consumer.auction.AuctionPage;
import it.esercitazione.liveauction.consumer.auction.AuctionSnapshot;
import it.esercitazione.liveauction.consumer.auction.RoomTicket;
import it.esercitazione.liveauction.consumer.auction.VictoryPage;
import it.esercitazione.liveauction.consumer.wallet.WalletPage;
import it.esercitazione.liveauction.consumer.wallet.WalletBalance;
import it.esercitazione.liveauction.consumer.admin.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.math.BigDecimal;
import java.time.Instant;

@Component
public class ProducerClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    public ProducerClient(RestClient producerRestClient, ObjectMapper mapper) {
        this.client = producerRestClient;
        this.mapper = mapper;
    }

    public RegistrationResponse register(RegistrationRequest request) {
        return call(() -> client.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(RegistrationResponse.class));
    }

    public LoginResponse login(LoginRequest request) {
        return call(() -> client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(LoginResponse.class));
    }

    public LoginResponse refresh(String refreshToken) {
        return call(() -> client.post().uri("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(refreshToken)).retrieve().body(LoginResponse.class));
    }

    public void logout(String accessToken) {
        call(() -> { client.post().uri("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve().toBodilessEntity(); return null; });
    }

    public void deleteAccount(String accessToken) {
        call(() -> { client.delete().uri("/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve().toBodilessEntity(); return null; });
    }

    public CategoriaView[] publicCategories() {
        return call(() -> client.get().uri("/categorie").retrieve().body(CategoriaView[].class));
    }

    public ProdottoPage publicProducts(String query, String categoria, Boolean astabile, int page, int size) {
        return call(() -> client.get().uri(builder -> {
            builder.path("/prodotti");
            if (query != null && !query.isBlank()) builder.queryParam("query", query);
            if (categoria != null && !categoria.isBlank()) builder.queryParam("categoria", categoria);
            if (astabile != null) builder.queryParam("astabile", astabile);
            return builder.queryParam("page", page).queryParam("size", size).build();
        }).retrieve().body(ProdottoPage.class));
    }

    public ProdottoView publicProduct(long id) {
        return call(() -> client.get().uri("/prodotti/{id}", id).retrieve().body(ProdottoView.class));
    }

    public AdminCategory[] adminCategories(String token) {
        return call(() -> client.get().uri("/admin/categorie").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve().body(AdminCategory[].class));
    }

    public AdminCategory saveAdminCategory(String token, Long id, CategoryForm form) {
        return call(() -> (id == null ? client.post().uri("/admin/categorie")
                : client.put().uri("/admin/categorie/{id}", id))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .body(form).retrieve().body(AdminCategory.class));
    }

    public AdminProductPage adminProducts(String token, String query, String category, Boolean auctionable,
                                          Boolean active, int page, int size) {
        return call(() -> client.get().uri(builder -> {
            builder.path("/admin/prodotti");
            if (query != null) builder.queryParam("query", query);
            if (category != null) builder.queryParam("categoria", category);
            if (auctionable != null) builder.queryParam("astabile", auctionable);
            if (active != null) builder.queryParam("attivo", active);
            return builder.queryParam("page", page).queryParam("size", size).build();
        }).header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(AdminProductPage.class));
    }

    public AdminProduct adminProduct(String token, long id) {
        return call(() -> client.get().uri("/admin/prodotti/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(AdminProduct.class));
    }

    public AdminProduct saveAdminProduct(String token, Long id, ProductForm form) {
        return call(() -> (id == null ? client.post().uri("/admin/prodotti")
                : client.put().uri("/admin/prodotti/{id}", id))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .body(id == null ? form.createRequest() : form.updateRequest())
                .retrieve().body(AdminProduct.class));
    }

    public ScheduledAuction scheduleAuction(String token, AuctionForm form) {
        return call(() -> client.post().uri("/admin/aste")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .body(form.request()).retrieve().body(ScheduledAuction.class));
    }

    public VictoryPage victories(String token, int page, int size) {
        return call(() -> client.get().uri(builder -> builder.path("/me/vittorie")
                .queryParam("page", page).queryParam("size", size).build())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(VictoryPage.class));
    }

    public AuctionHistoryPage auctionHistory(String token, Long productId, Long winnerId,
            String state, Instant from, Instant to, int page, int size) {
        return call(() -> client.get().uri(builder -> {
            builder.path("/admin/aste/storico");
            if (productId != null) builder.queryParam("prodottoId", productId);
            if (winnerId != null) builder.queryParam("vincitoreId", winnerId);
            if (state != null) builder.queryParam("stato", state);
            if (from != null) builder.queryParam("da", from.toString());
            if (to != null) builder.queryParam("a", to.toString());
            return builder.queryParam("page", page).queryParam("size", size).build();
        }).header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(AuctionHistoryPage.class));
    }

    public CancellationResult cancelAuction(String token, long id) {
        return call(() -> client.post().uri("/admin/aste/{id}/annullamento", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(CancellationResult.class));
    }

    public AuctionPage publicAuctions(String stato, String categoria, String query, int page, int size) {
        return call(() -> client.get().uri(builder -> {
            builder.path("/aste");
            if (stato != null) builder.queryParam("stato", stato);
            if (categoria != null) builder.queryParam("categoria", categoria);
            if (query != null) builder.queryParam("query", query);
            return builder.queryParam("page", page).queryParam("size", size).build();
        }).retrieve().body(AuctionPage.class));
    }

    public AuctionSnapshot auctionSnapshot(long id) {
        return call(() -> client.get().uri("/aste/{id}", id).retrieve().body(AuctionSnapshot.class));
    }

    public RoomTicket auctionTicket(long id, String accessToken) {
        return call(() -> client.post().uri("/aste/{id}/ticket", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve().body(RoomTicket.class));
    }

    public WalletPage wallet(String accessToken, int page, int size) {
        return call(() -> client.get().uri(builder -> builder.path("/me/portafoglio")
                .queryParam("page", page).queryParam("size", size).build())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve().body(WalletPage.class));
    }

    public it.esercitazione.liveauction.consumer.inventario.InventoryPage inventory(String token, int page, int size) {
        return call(() -> client.get().uri(builder -> builder.path("/me/inventario")
                .queryParam("page", page).queryParam("size", size).build())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve()
                .body(it.esercitazione.liveauction.consumer.inventario.InventoryPage.class));
    }

    public WalletBalance setWalletBalance(String accessToken, BigDecimal total) {
        return call(() -> client.put().uri("/me/portafoglio/impostazioni")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WalletBalanceRequest(total)).retrieve().body(WalletBalance.class));
    }

    private record WalletBalanceRequest(BigDecimal saldoTotale) {}

    public it.esercitazione.liveauction.consumer.acquisto.PurchaseResult buy(String token,long product,
            java.util.UUID clientPurchaseId,int quantity) {
        return call(()->client.post().uri("/prodotti/{id}/acquisti",product)
                .header(HttpHeaders.AUTHORIZATION,bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .body(new PurchaseRequest(clientPurchaseId,quantity)).retrieve()
                .body(it.esercitazione.liveauction.consumer.acquisto.PurchaseResult.class));
    }
    private record PurchaseRequest(java.util.UUID clientPurchaseId,int quantita) {}

    // Shared entry point for later domain clients: supply the session token, never a browser token.
    public <T> T getProtected(String path, String accessToken, Class<T> responseType) {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains(":") || path.contains("?")) {
            throw new IllegalArgumentException("Expected a fixed Producer path");
        }
        return call(() -> client.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve().body(responseType));
    }

    private String bearer(String token) { return "Bearer " + token; }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) throw new ProducerUnavailableException();
            String code = null;
            if (ex.getResponseHeaders() != null && MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(
                    ex.getResponseHeaders().getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                            : ex.getResponseHeaders().getContentType())) {
                try {
                    JsonNode problem = mapper.readTree(ex.getResponseBodyAsByteArray());
                    if (problem.hasNonNull("code")) code = problem.get("code").asText();
                } catch (Exception ignored) { /* An invalid error body remains a status-only error. */ }
            }
            throw new ProducerException(ex.getStatusCode().value(), code);
        } catch (ResourceAccessException ex) {
            throw new ProducerUnavailableException();
        } catch (RestClientException ex) {
            throw new ProducerUnavailableException();
        }
    }

    @Configuration
    static class HttpConfig {
        @Bean
        RestClient producerRestClient(@Value("${liveauction.producer.base-url}") String baseUrl,
                @Value("${liveauction.producer.connect-timeout}") Duration connectTimeout,
                @Value("${liveauction.producer.read-timeout}") Duration readTimeout) {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        }
    }
}
