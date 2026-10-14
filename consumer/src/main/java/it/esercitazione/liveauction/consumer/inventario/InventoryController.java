package it.esercitazione.liveauction.consumer.inventario;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.auth.SessionExpiredException;
import it.esercitazione.liveauction.consumer.client.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class InventoryController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    @GetMapping("/inventario")
    public String inventory(@RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        int p, s;
        try {
            p = Integer.parseInt(page); s = Integer.parseInt(size);
            if (p < 0 || s < 1 || s > 100) throw new NumberFormatException();
        } catch (NumberFormatException ex) { return error(response, model, 400, "Invalid page or size."); }
        try {
            var result = sessions.withAccess(request.getSession(false), token -> producer.inventory(token, p, s));
            if (result == null || result.content() == null) throw new ProducerUnavailableException();
            model.addAttribute("inventory", result);
            return "inventory/page";
        } catch (SessionExpiredException ex) { return "redirect:/login?expired=1"; }
        catch (ProducerUnavailableException ex) { return error(response, model, 503, "Inventory is temporarily unavailable."); }
        catch (ProducerException ex) {
            return error(response, model, ex.status() == 400 ? 400 : ex.status() == 403 ? 403 : 502,
                    "Inventory could not be loaded.");
        }
    }

    private String error(HttpServletResponse response, Model model, int status, String message) {
        response.setStatus(status);
        model.addAttribute("errorTitle", "Inventory unavailable");
        model.addAttribute("errorMessage", message);
        return "auction/error";
    }
}
