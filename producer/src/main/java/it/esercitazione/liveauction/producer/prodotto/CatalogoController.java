package it.esercitazione.liveauction.producer.prodotto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Catalogo", description = "Categorie e prodotti pubblici: solo elementi attivi, senza autenticazione.")
public class CatalogoController {
    private final CategoriaService categoriaService;
    private final ProdottoService prodottoService;

    @GetMapping("/categorie")
    @Operation(summary = "Elenca le categorie attive", description = "Rotta pubblica. Array di categorie attive ordinate per nome.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Categorie attive."))
    public List<CategoriaResponse> categorie() {
        return categoriaService.elencaAttive();
    }

    @GetMapping("/prodotti")
    @Operation(summary = "Cerca nel catalogo",
            description = "Rotta pubblica. Pagina di prodotti attivi di categorie attive, con ricerca testuale e filtri combinabili. "
                    + "Ordinamento stabile per nome e ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina di prodotti (può essere vuota)."),
            @ApiResponse(responseCode = "400", description = "Parametri non validi: `page` negativo, `size` fuori da 1-100 o `astabile` non booleano.")
    })
    public PaginaResponse<ProdottoResponse> prodotti(
            @Parameter(description = "Testo cercato senza distinzione tra maiuscole e minuscole in nome, SKU e descrizione; `%` e `_` sono letterali.", example = "laptop") @RequestParam(required = false) String query,
            @Parameter(description = "Slug esatto della categoria.", example = "informatica") @RequestParam(required = false) String categoria,
            @Parameter(description = "Se true solo prodotti abilitati alle aste, se false solo gli altri; omesso = tutti.", example = "true") @RequestParam(required = false) Boolean astabile,
            @Parameter(description = "Indice della pagina, parte da 0.", example = "0") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 12).", example = "12") @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return prodottoService.cercaCatalogo(query, categoria, astabile, page, size);
    }

    @GetMapping("/prodotti/{id}")
    @Operation(summary = "Dettaglio di un prodotto",
            description = "Rotta pubblica. Mostra stock disponibile e bloccato, `astabile` e numero di aste programmate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dettaglio del prodotto."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: prodotto inesistente, disattivato o di una categoria disattivata.")
    })
    public ProdottoResponse prodotto(@Parameter(description = "ID del prodotto.", example = "1") @PathVariable long id) {
        return prodottoService.dettaglioCatalogo(id);
    }
}
