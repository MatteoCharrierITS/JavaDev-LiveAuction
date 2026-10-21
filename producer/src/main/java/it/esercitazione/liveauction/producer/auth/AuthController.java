package it.esercitazione.liveauction.producer.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.esercitazione.liveauction.producer.auth.requests.LoginRequest;
import it.esercitazione.liveauction.producer.auth.requests.RefreshRequest;
import it.esercitazione.liveauction.producer.auth.requests.RegistrazioneRequest;
import it.esercitazione.liveauction.producer.auth.responses.LoginResponse;
import it.esercitazione.liveauction.producer.auth.responses.RegistrazioneResponse;
import it.esercitazione.liveauction.producer.auth.services.LoginService;
import it.esercitazione.liveauction.producer.auth.services.RegistrazioneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticazione", description = "Registrazione, login, rinnovo dei token e logout.")
public class AuthController {
    private final RegistrazioneService registrazioneService;
    private final LoginService loginService;

    @PostMapping("/register")
    @Operation(summary = "Registra un nuovo utente",
            description = "Crea un utente con ruolo `USER` e un portafoglio con il credito iniziale configurato "
                    + "(default 10.000 CRD, movimento `CREDITO_INIZIALE`). L'importo non è mai accettato dal client.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Utente creato.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrazioneResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido: username (3-50 caratteri), email o password (8-72 caratteri) mancanti o non validi."),
            @ApiResponse(responseCode = "409", description = "Username o email già in uso.")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrazioneResponse registra(@Valid @RequestBody RegistrazioneRequest request) {
        return registrazioneService.registra(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Effettua il login",
            description = "Restituisce `accessToken` (JWT, 30 minuti) e `refreshToken` (7 giorni). Copia `accessToken` "
                    + "nel pulsante **Authorize** per chiamare le rotte protette.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login riuscito.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido: username o password mancanti."),
            @ApiResponse(responseCode = "401", description = "Credenziali errate o account disattivato.")
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rinnova i token",
            description = "Scambia il refresh token con una nuova coppia di token, senza header Bearer. "
                    + "Il refresh token precedente smette di funzionare; la scadenza della sessione resta quella fissata al login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token rinnovati.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido: `refreshToken` mancante."),
            @ApiResponse(responseCode = "401", description = "Refresh token non valido, già sostituito, scaduto o sessione revocata.")
    })
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return loginService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Effettua il logout",
            description = "Revoca la sessione corrente: access token e refresh token di quella sessione diventano inutilizzabili.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Sessione revocata, nessun contenuto."))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(JwtAuthenticationToken authentication) {
        UUID sessionId = UUID.fromString(authentication.getToken().getClaimAsString("sid"));
        long userId = Long.parseLong(authentication.getToken().getSubject());
        loginService.logout(sessionId, userId);
    }
}
