package it.esercitazione.liveauction.producer.prodotto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Catalogo ADMIN", description = "Gestione di categorie e prodotti, anche non pubblici. Richiede il ruolo ADMIN.")
public class AdminCatalogoController {
    private final CategoriaService categoriaService;
    private final ProdottoService prodottoService;

    @GetMapping("/categorie")
    @Operation(summary = "Elenca tutte le categorie", description = "Include anche le categorie disattivate.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Tutte le categorie, ordinate per nome."))
    public List<CategoriaResponse> categorie() {
        return categoriaService.elencaTutte();
    }

    @PostMapping("/categorie")
    @Operation(summary = "Crea una categoria",
            description = "`nome` e `slug` sono univoci; `slug` usa lettere minuscole, cifre e trattini. `attiva` è facoltativo e vale true in creazione.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria creata.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido (nome vuoto, slug non conforme, JSON malformato)."),
            @ApiResponse(responseCode = "409", description = "`CATEGORIA_GIA_ESISTENTE`: nome o slug duplicati.")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse creaCategoria(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crea(request);
    }

    @PutMapping("/categorie/{id}")
    @Operation(summary = "Modifica una categoria",
            description = "Sostituisce nome e slug; `attiva` omesso conserva il valore corrente. Disattivare una categoria nasconde i suoi prodotti al pubblico senza modificare stock o aste.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria aggiornata.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CategoriaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: categoria inesistente."),
            @ApiResponse(responseCode = "409", description = "`CATEGORIA_GIA_ESISTENTE`: nome o slug duplicati.")
    })
    public CategoriaResponse modificaCategoria(@Parameter(description = "ID della categoria.", example = "1") @PathVariable long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.modifica(id, request);
    }

    @GetMapping("/prodotti")
    @Operation(summary = "Cerca nel catalogo completo",
            description = "Come il catalogo pubblico, ma include prodotti disattivati e di categorie disattivate; accetta anche il filtro `attivo`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina di prodotti (può essere vuota)."),
            @ApiResponse(responseCode = "400", description = "Parametri non validi: `page` negativo, `size` fuori da 1-100 o booleani non validi.")
    })
    public PaginaResponse<ProdottoResponse> prodotti(
            @Parameter(description = "Testo cercato senza distinzione tra maiuscole e minuscole in nome, SKU e descrizione; `%` e `_` sono letterali.", example = "laptop") @RequestParam(required = false) String query,
            @Parameter(description = "Slug esatto della categoria.", example = "informatica") @RequestParam(required = false) String categoria,
            @Parameter(description = "Se true solo prodotti abilitati alle aste, se false solo gli altri; omesso = tutti.", example = "true") @RequestParam(required = false) Boolean astabile,
            @Parameter(description = "Solo ADMIN: true = solo prodotti attivi, false = solo disattivati; omesso = entrambi.", example = "true") @RequestParam(required = false) Boolean attivo,
            @Parameter(description = "Indice della pagina, parte da 0.", example = "0") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 12).", example = "12") @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return prodottoService.cercaAdmin(query, categoria, astabile, attivo, page, size);
    }

    @GetMapping("/prodotti/{id}")
    @Operation(summary = "Dettaglio di un prodotto (ADMIN)", description = "Mostra il prodotto anche se disattivato o di una categoria disattivata.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dettaglio del prodotto."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: prodotto inesistente.")
    })
    public ProdottoResponse prodotto(@Parameter(description = "ID del prodotto.", example = "1") @PathVariable long id) {
        return prodottoService.dettaglioAdmin(id);
    }

    @PostMapping("/prodotti")
    @Operation(summary = "Crea un prodotto",
            description = "Il prodotto nasce attivo, con quantità bloccata e versione a zero. Lo SKU è normalizzato in maiuscolo. "
                    + "`prezzoFisso` è facoltativo ma serve per renderlo acquistabile.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Prodotto creato.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProdottoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: la categoria `categoriaId` non esiste."),
            @ApiResponse(responseCode = "409", description = "`SKU_GIA_UTILIZZATO`.")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ProdottoResponse creaProdotto(@Valid @RequestBody NuovoProdottoRequest request) {
        return prodottoService.crea(request);
    }

    @PutMapping("/prodotti/{id}")
    @Operation(summary = "Modifica un prodotto",
            description = "Sostituisce tutti i campi modificabili: i facoltativi omessi vengono azzerati. `versione` deve essere quella dell'ultima lettura "
                    + "(controllo di concorrenza ottimistica). La disattivazione (`attivo: false`) sostituisce la cancellazione, che non esiste.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prodotto aggiornato.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProdottoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body non valido."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: prodotto o categoria inesistenti."),
            @ApiResponse(responseCode = "409", description = "`SKU_GIA_UTILIZZATO` oppure `VERSIONE_NON_AGGIORNATA` (rileggere il prodotto prima di riprovare).")
    })
    public ProdottoResponse modificaProdotto(@Parameter(description = "ID del prodotto.", example = "1") @PathVariable long id, @Valid @RequestBody ModificaProdottoRequest request) {
        return prodottoService.modifica(id, request);
    }
}
