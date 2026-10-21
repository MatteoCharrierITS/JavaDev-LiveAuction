package it.esercitazione.liveauction.consumer.admin;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.auth.SessionExpiredException;
import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Controller
public class AdminAuctionHistoryController {
    private static final Set<String> STATES = Set.of("CHIUSA", "ANNULLATA");
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    public AdminAuctionHistoryController(ProducerClient producer, AuthSessionService sessions) {
        this.producer = producer;
        this.sessions = sessions;
    }

    @GetMapping("/admin/aste/storico")
    public String history(@RequestParam(required = false) String prodottoId,
            @RequestParam(required = false) String vincitoreId,
            @RequestParam(required = false) String stato,
            @RequestParam(required = false) String da, @RequestParam(required = false) String a,
            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "20") String size,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        Long product, winner;
        Instant from, to;
        int p, s;
        String state = clean(stato);
        try {
            product = positiveId(prodottoId); winner = positiveId(vincitoreId);
            from = instant(da); to = instant(a);
            p = Integer.parseInt(page); s = Integer.parseInt(size);
            if (p < 0 || s < 1 || s > 100 || (state != null && !STATES.contains(state))
                    || (from != null && to != null && from.isAfter(to))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | DateTimeException ex) {
            return error(response, model, HttpStatus.BAD_REQUEST, "Check the history filters, dates, page, and size.");
        }
        try {
            AuctionHistoryPage result = sessions.withAccess(request.getSession(false),
                    token -> producer.auctionHistory(token, product, winner, state, from, to, p, s));
            if (result == null || result.content() == null) throw new ProducerUnavailableException();
            model.addAttribute("history", result);
            model.addAttribute("prodottoId", product);
            model.addAttribute("vincitoreId", winner);
            model.addAttribute("stato", state);
            model.addAttribute("da", clean(da));
            model.addAttribute("a", clean(a));
            return "admin/auction-history";
        } catch (SessionExpiredException ex) { return "redirect:/login?expired=1"; }
        catch (ProducerUnavailableException ex) { return error(response, model, HttpStatus.SERVICE_UNAVAILABLE, "Auction history is temporarily unavailable."); }
        catch (ProducerException ex) {
            return error(response, model, ex.status() == 400 ? HttpStatus.BAD_REQUEST
                    : ex.status() == 403 ? HttpStatus.FORBIDDEN : HttpStatus.BAD_GATEWAY,
                    ex.status() == 400 ? "The Producer rejected these filters." : "Auction history could not be loaded.");
        }
    }

    @PostMapping("/admin/aste/{id}/annullamento")
    public String cancel(@PathVariable long id, HttpServletRequest request,
            HttpServletResponse response, Model model, RedirectAttributes flash) {
        response.setHeader("Cache-Control", "no-store");
        if (id < 1) return error(response, model, HttpStatus.BAD_REQUEST, "Choose a valid auction.");
        try {
            CancellationResult result = sessions.withAccess(request.getSession(false),
                    token -> producer.cancelAuction(token, id));
            if (result == null || result.id() != id || !"ANNULLATA".equals(result.stato()))
                throw new ProducerUnavailableException();
            flash.addFlashAttribute("notice", "Auction #" + id + " cancelled. The reserved unit is available again.");
            return "redirect:/aste";
        } catch (SessionExpiredException ex) { return "redirect:/login?expired=1"; }
        catch (ProducerUnavailableException ex) { return error(response, model, HttpStatus.SERVICE_UNAVAILABLE, "Cancellation could not be confirmed. Reload the auction before trying again."); }
        catch (ProducerException ex) {
            HttpStatus status = switch (ex.status()) {
                case 400 -> HttpStatus.BAD_REQUEST;
                case 403 -> HttpStatus.FORBIDDEN;
                case 404 -> HttpStatus.NOT_FOUND;
                case 409 -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            String message = switch (ex.code() == null ? "" : ex.code()) {
                case "ASTA_CON_OFFERTE" -> "This auction has received an offer and cannot be cancelled.";
                case "ASTA_NON_ANNULLABILE" -> "This auction is closed or has expired and cannot be cancelled.";
                case "CHIUSURA_IN_CORSO" -> "This auction is closing. If it is still open one minute after its end, try again.";
                case "RISORSA_NON_TROVATA" -> "This auction no longer exists.";
                default -> "The Producer could not cancel this auction. Check its current state.";
            };
            return error(response, model, status, message);
        }
    }

    private static Long positiveId(String value) {
        if (clean(value) == null) return null;
        long id = Long.parseLong(value.trim());
        if (id < 1) throw new IllegalArgumentException();
        return id;
    }
    private static Instant instant(String value) throws DateTimeParseException {
        return clean(value) == null ? null : Instant.parse(value.trim());
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String error(HttpServletResponse response, Model model, HttpStatus status, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorMessage", message);
        return "admin/error";
    }
}
