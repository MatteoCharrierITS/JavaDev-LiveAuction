package it.esercitazione.liveauction.producer.portafoglio;

import it.esercitazione.liveauction.producer.portafoglio.requests.ImpostaSaldoRequest;
import it.esercitazione.liveauction.producer.portafoglio.responses.PortafoglioResponse;
import it.esercitazione.liveauction.producer.portafoglio.responses.SaldoResponse;
import it.esercitazione.liveauction.producer.portafoglio.services.PortafoglioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/portafoglio")
@RequiredArgsConstructor
public class PortafoglioController {
    private final PortafoglioService service;

    @GetMapping
    public PortafoglioResponse leggi(JwtAuthenticationToken authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.leggi(Long.parseLong(authentication.getToken().getSubject()), page, size);
    }

    @PutMapping("/impostazioni")
    public SaldoResponse imposta(JwtAuthenticationToken authentication,
                                @Valid @RequestBody ImpostaSaldoRequest request) {
        return service.imposta(Long.parseLong(authentication.getToken().getSubject()), request);
    }
}
