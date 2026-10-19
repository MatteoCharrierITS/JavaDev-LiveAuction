package it.esercitazione.liveauction.consumer.web;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.wallet.WalletDisplay;
import it.esercitazione.liveauction.consumer.wallet.WalletPage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saldo disponibile per la navbar: la pagina lo carica dopo il rendering, così non rallenta né rompe le pagine. */
@RestController
public class NavBalanceController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    public NavBalanceController(ProducerClient producer, AuthSessionService sessions) {
        this.producer = producer;
        this.sessions = sessions;
    }

    @GetMapping("/me/saldo")
    public ResponseEntity<Balance> balance(HttpServletRequest request) {
        try {
            WalletPage wallet = sessions.withAccess(request.getSession(false), token -> producer.wallet(token, 0, 1));
            if (wallet == null || wallet.saldoDisponibile() == null) return unavailable();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new Balance(new WalletDisplay().amount(wallet.saldoDisponibile()), wallet.valuta()));
        } catch (RuntimeException ex) {
            return unavailable();
        }
    }

    private static ResponseEntity<Balance> unavailable() {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).build();
    }

    public record Balance(String disponibile, String valuta) {}
}
