package it.esercitazione.liveauction.producer.acquisto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AcquistoController {
    private final AcquistoService service;
    @PostMapping("/api/v1/prodotti/{id}/acquisti")
    public ResponseEntity<AcquistoResponse> compra(@PathVariable long id,
            @Valid @RequestBody AcquistoRequest request, JwtAuthenticationToken auth) {
        var result=service.compra(Long.parseLong(auth.getToken().getSubject()),id,request);
        return ResponseEntity.status(result.duplicato()?200:201).cacheControl(CacheControl.noStore()).body(result);
    }
}
