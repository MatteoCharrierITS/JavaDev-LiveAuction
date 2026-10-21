package it.esercitazione.liveauction.consumer.auction;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.auth.SessionExpiredException;
import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import it.esercitazione.liveauction.consumer.wallet.WalletPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;

@Controller
public class AuctionRoomController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;
    private final String websocketUrl;

    public AuctionRoomController(ProducerClient producer, AuthSessionService sessions,
            @Value("${liveauction.producer.websocket-url}") String websocketUrl) {
        this.producer = producer;
        this.sessions = sessions;
        this.websocketUrl = websocketUrl;
    }

    @GetMapping("/aste/{id}")
    public String room(@PathVariable long id, Model model, HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (id < 1) return pageError(model, response, HttpStatus.BAD_REQUEST, "Invalid auction", "Choose a valid auction.");
        try {
            AuctionSnapshot snapshot = checkedSnapshot(id);
            model.addAttribute("auction", snapshot);
            model.addAttribute("websocketUrl", websocketUrl);
            // ADMIN watches the room read-only: no bid form, no wallet, no presence entry.
            model.addAttribute("observer", request.isUserInRole("ADMIN"));
            return "auctions/room";
        } catch (ProducerException ex) {
            return pageError(model, response, ex.status() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY,
                    "Auction unavailable", ex.status() == 404 ? "This auction does not exist." : "The auction could not be loaded.");
        } catch (ProducerUnavailableException ex) {
            return pageError(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Auction unavailable", "The auction service is temporarily unavailable.");
        }
    }

    @GetMapping("/aste/{id}/snapshot")
    public ResponseEntity<?> snapshot(@PathVariable long id) {
        if (id < 1) return failure(HttpStatus.BAD_REQUEST, "INVALID_AUCTION");
        try {
            return noStore(checkedSnapshot(id));
        } catch (ProducerException ex) {
            return failure(status(ex.status()), ex.code());
        } catch (ProducerUnavailableException ex) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCER_UNAVAILABLE");
        }
    }

    @GetMapping("/aste/{id}/wallet")
    public ResponseEntity<?> wallet(@PathVariable long id, HttpServletRequest request) {
        if (id < 1) return failure(HttpStatus.BAD_REQUEST, "INVALID_AUCTION");
        try {
            WalletPage wallet = sessions.withAccess(request.getSession(false), token -> producer.wallet(token, 0, 1));
            if (wallet == null || wallet.saldoTotale() == null || wallet.saldoRiservato() == null
                    || wallet.saldoDisponibile() == null) throw new ProducerUnavailableException();
            return noStore(new RoomWallet(wallet.saldoTotale(), wallet.saldoRiservato(), wallet.saldoDisponibile(), wallet.valuta()));
        } catch (SessionExpiredException ex) {
            return failure(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED");
        } catch (ProducerException ex) {
            return failure(status(ex.status()), ex.code());
        } catch (ProducerUnavailableException ex) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCER_UNAVAILABLE");
        }
    }

    @PostMapping("/aste/{id}/ticket")
    public ResponseEntity<?> ticket(@PathVariable long id, HttpServletRequest request) {
        if (id < 1) return failure(HttpStatus.BAD_REQUEST, "INVALID_AUCTION");
        try {
            RoomTicket issued = sessions.withAccess(request.getSession(false), token -> producer.auctionTicket(id, token));
            if (issued == null || issued.ticket() == null || issued.ticket().isBlank()) throw new ProducerUnavailableException();
            return noStore(new TicketOnly(issued.ticket()));
        } catch (SessionExpiredException ex) {
            return failure(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED");
        } catch (ProducerException ex) {
            return failure(status(ex.status()), ex.code());
        } catch (ProducerUnavailableException ex) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCER_UNAVAILABLE");
        }
    }

    private AuctionSnapshot checkedSnapshot(long id) {
        AuctionSnapshot result = producer.auctionSnapshot(id);
        if (result == null || result.id() == null || result.id() != id || result.serverTime() == null
                || result.stato() == null || result.prodotto() == null || result.inizioAt() == null
                || result.fineAt() == null || result.prezzoIniziale() == null) throw new ProducerUnavailableException();
        return result;
    }

    private static HttpStatus status(int value) {
        HttpStatus result = HttpStatus.resolve(value);
        return result != null && result.is4xxClientError() ? result : HttpStatus.BAD_GATEWAY;
    }

    private static ResponseEntity<?> failure(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new RoomError(code == null ? "REQUEST_FAILED" : code));
    }

    private static ResponseEntity<?> noStore(Object body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static String pageError(Model model, HttpServletResponse response, HttpStatus status,
            String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorTitle", title);
        model.addAttribute("errorMessage", message);
        return "auction/error";
    }

    public record RoomWallet(BigDecimal saldoTotale, BigDecimal saldoRiservato,
            BigDecimal saldoDisponibile, String valuta) {}
    public record TicketOnly(String ticket) {
        @Override public String toString() { return "TicketOnly[redacted]"; }
    }
    public record RoomError(String code) {}
}
