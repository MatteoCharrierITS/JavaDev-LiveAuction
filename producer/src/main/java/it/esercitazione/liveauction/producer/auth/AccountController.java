package it.esercitazione.liveauction.producer.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.esercitazione.liveauction.producer.auth.services.EliminazioneUtenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Gestione del proprio account.")
public class AccountController {
    private final EliminazioneUtenteService eliminazioneUtenteService;

    @DeleteMapping
    @Operation(summary = "Elimina il proprio account",
            description = "Disattiva l'account, anonimizza username, email e password e revoca tutte le sessioni. "
                    + "Gli ID e le relazioni storiche restano nel database; username ed email originali possono essere registrati di nuovo.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Account disattivato e anonimizzato, nessun contenuto."))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(JwtAuthenticationToken authentication) {
        eliminazioneUtenteService.elimina(Long.parseLong(authentication.getToken().getSubject()));
    }
}
