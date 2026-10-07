package it.esercitazione.liveauction.producer.prodotto;

import it.esercitazione.liveauction.producer.prodotto.requests.CategoriaRequest;
import it.esercitazione.liveauction.producer.prodotto.requests.ModificaProdottoRequest;
import it.esercitazione.liveauction.producer.prodotto.requests.NuovoProdottoRequest;
import it.esercitazione.liveauction.producer.prodotto.responses.CategoriaResponse;
import it.esercitazione.liveauction.producer.prodotto.responses.PaginaResponse;
import it.esercitazione.liveauction.producer.prodotto.responses.ProdottoResponse;
import it.esercitazione.liveauction.producer.prodotto.services.CategoriaService;
import it.esercitazione.liveauction.producer.prodotto.services.ProdottoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCatalogoController {
    private final CategoriaService categoriaService;
    private final ProdottoService prodottoService;

    @GetMapping("/categorie")
    public List<CategoriaResponse> categorie() {
        return categoriaService.elencaTutte();
    }

    @PostMapping("/categorie")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse creaCategoria(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crea(request);
    }

    @PutMapping("/categorie/{id}")
    public CategoriaResponse modificaCategoria(@PathVariable long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.modifica(id, request);
    }

    @GetMapping("/prodotti")
    public PaginaResponse<ProdottoResponse> prodotti(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean astabile,
            @RequestParam(required = false) Boolean attivo,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return prodottoService.cercaAdmin(query, categoria, astabile, attivo, page, size);
    }

    @GetMapping("/prodotti/{id}")
    public ProdottoResponse prodotto(@PathVariable long id) {
        return prodottoService.dettaglioAdmin(id);
    }

    @PostMapping("/prodotti")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdottoResponse creaProdotto(@Valid @RequestBody NuovoProdottoRequest request) {
        return prodottoService.crea(request);
    }

    @PutMapping("/prodotti/{id}")
    public ProdottoResponse modificaProdotto(@PathVariable long id, @Valid @RequestBody ModificaProdottoRequest request) {
        return prodottoService.modifica(id, request);
    }
}
