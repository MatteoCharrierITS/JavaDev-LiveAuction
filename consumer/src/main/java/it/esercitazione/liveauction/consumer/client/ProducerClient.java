package it.esercitazione.liveauction.consumer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.consumer.dto.*;
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
