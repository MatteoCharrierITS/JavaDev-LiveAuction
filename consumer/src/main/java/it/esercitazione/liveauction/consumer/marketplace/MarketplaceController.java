package it.esercitazione.liveauction.consumer.marketplace;

import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MarketplaceController {
    private final ProducerClient producer;

    public MarketplaceController(ProducerClient producer) {
        this.producer = producer;
    }

    @GetMapping("/marketplace")
    public String list(@RequestParam(required = false) String query,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(required = false) Boolean astabile,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "12") int size,
                       Model model, HttpServletResponse response) {
        if (page < 0 || size < 1 || size > 100) {
            return error(model, response, HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater, and size must be between 1 and 100.");
        }
        String search = emptyToNull(query);
        String category = emptyToNull(categoria);
        try {
            model.addAttribute("categories", producer.publicCategories());
            model.addAttribute("products", producer.publicProducts(search, category, astabile, page, size));
            model.addAttribute("query", search);
            model.addAttribute("categoria", category);
            model.addAttribute("astabile", astabile);
            return "marketplace/list";
        } catch (ProducerUnavailableException ex) {
            return error(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "The catalogue is temporarily unavailable. Please try again.");
        } catch (ProducerException ex) {
            return error(model, response, HttpStatus.BAD_GATEWAY,
                    "The catalogue could not be loaded. Please try again.");
        }
    }

    @GetMapping("/prodotti/{id}")
    public String detail(@PathVariable long id, Model model, HttpServletResponse response) {
        try {
            model.addAttribute("product", producer.publicProduct(id));
            return "marketplace/detail";
        } catch (ProducerException ex) {
            if (ex.status() == 404) {
                return error(model, response, HttpStatus.NOT_FOUND,
                        "This product is not available in the public catalogue.");
            }
            return error(model, response, HttpStatus.BAD_GATEWAY,
                    "The product could not be loaded. Please try again.");
        } catch (ProducerUnavailableException ex) {
            return error(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "The catalogue is temporarily unavailable. Please try again.");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String error(Model model, HttpServletResponse response, HttpStatus status, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorTitle", switch (status) {
            case NOT_FOUND -> "Product not found";
            case BAD_REQUEST -> "Invalid filters";
            default -> "Catalogue unavailable";
        });
        model.addAttribute("errorMessage", message);
        return "marketplace/error";
    }
}
