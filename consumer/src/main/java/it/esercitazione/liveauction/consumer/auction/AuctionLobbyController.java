package it.esercitazione.liveauction.consumer.auction;

import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

@Controller
public class AuctionLobbyController {
    private static final Set<String> STATES = Set.of("PROGRAMMATA", "STANZA_APERTA", "APERTA", "CHIUSA", "ANNULLATA");
    private final ProducerClient producer;

    public AuctionLobbyController(ProducerClient producer) { this.producer = producer; }

    @GetMapping("/aste")
    public String lobby(@RequestParam(required = false) String stato,
                        @RequestParam(required = false) String categoria,
                        @RequestParam(required = false) String query,
                        @RequestParam(defaultValue = "0") String page,
                        @RequestParam(defaultValue = "12") String size,
                        Model model, HttpServletResponse response) {
        String state = clean(stato);
        String category = clean(categoria);
        String search = clean(query);
        int pageNumber;
        int pageSize;
        try {
            pageNumber = Integer.parseInt(page);
            pageSize = Integer.parseInt(size);
        } catch (NumberFormatException ex) {
            return error(model, response, HttpStatus.BAD_REQUEST, "Invalid filters",
                    "Page must be zero or greater, and size must be between 1 and 100.");
        }
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100 || (state != null && !STATES.contains(state))) {
            return error(model, response, HttpStatus.BAD_REQUEST, "Invalid filters",
                    "Choose a listed auction state, a page of zero or greater, and a size from 1 to 100.");
        }
        try {
            AuctionPage auctions = producer.publicAuctions(state, category, search, pageNumber, pageSize);
            if (auctions == null || auctions.content() == null || auctions.serverTime() == null) {
                throw new ProducerUnavailableException();
            }
            model.addAttribute("auctions", auctions);
            model.addAttribute("stato", state);
            model.addAttribute("categoria", category);
            model.addAttribute("query", search);
            return "auction/lobby";
        } catch (ProducerUnavailableException ex) {
            return error(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Auctions unavailable",
                    "The auction service is temporarily unavailable. Please try again.");
        } catch (ProducerException ex) {
            if (ex.status() == 400) {
                return error(model, response, HttpStatus.BAD_REQUEST, "Invalid filters",
                        "The auction filters are invalid. Please check them and try again.");
            }
            return error(model, response, HttpStatus.BAD_GATEWAY, "Auctions unavailable",
                    "The auctions could not be loaded. Please try again.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String error(Model model, HttpServletResponse response, HttpStatus status,
                                String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorTitle", title);
        model.addAttribute("errorMessage", message);
        return "auction/error";
    }
}
