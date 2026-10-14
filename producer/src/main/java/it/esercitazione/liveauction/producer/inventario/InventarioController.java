package it.esercitazione.liveauction.producer.inventario;

import it.esercitazione.liveauction.producer.inventario.responses.PaginaInventarioResponse;
import it.esercitazione.liveauction.producer.inventario.services.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/inventario")
@RequiredArgsConstructor
public class InventarioController {
    private final InventarioService service;

    @GetMapping
    public ResponseEntity<PaginaInventarioResponse> personale(JwtAuthenticationToken auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.personale(Long.parseLong(auth.getToken().getSubject()), page, size));
    }
}
