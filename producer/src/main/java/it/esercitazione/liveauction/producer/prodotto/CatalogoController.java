package it.esercitazione.liveauction.producer.prodotto;

import it.esercitazione.liveauction.producer.prodotto.responses.CategoriaResponse;
import it.esercitazione.liveauction.producer.prodotto.responses.PaginaResponse;
import it.esercitazione.liveauction.producer.prodotto.responses.ProdottoResponse;
import it.esercitazione.liveauction.producer.prodotto.services.CategoriaService;
import it.esercitazione.liveauction.producer.prodotto.services.ProdottoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogoController {
    private final CategoriaService categoriaService;
    private final ProdottoService prodottoService;

    @GetMapping("/categorie")
    public List<CategoriaResponse> categorie() {
        return categoriaService.elencaAttive();
    }

    @GetMapping("/prodotti")
    public PaginaResponse<ProdottoResponse> prodotti(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean astabile,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return prodottoService.cercaCatalogo(query, categoria, astabile, page, size);
    }

    @GetMapping("/prodotti/{id}")
    public ProdottoResponse prodotto(@PathVariable long id) {
        return prodottoService.dettaglioCatalogo(id);
    }
}
