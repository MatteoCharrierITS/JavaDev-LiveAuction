package it.esercitazione.liveauction.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageTests {
    @Autowired TestRestTemplate browser;

    private org.springframework.http.ResponseEntity<String> browse(String path) {
        var headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        return browser.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test void missingPageRendersAccessible404() {
        var response = browse("/this-page-does-not-exist");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("<h1>Page not found</h1>", "role=\"alert\"");
    }

    @Test void unexpectedErrorRendersAccessible500() {
        var response = browse("/error");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("<h1>Something went wrong</h1>", "role=\"alert\"");
    }

    @Test void rejectedPostRendersAccessible403() {
        var headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        var response = browser.exchange("/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("<h1>Access denied</h1>", "role=\"alert\"");
    }
}
