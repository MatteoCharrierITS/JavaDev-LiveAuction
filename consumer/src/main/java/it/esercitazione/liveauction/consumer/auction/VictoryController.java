package it.esercitazione.liveauction.consumer.auction;

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
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class VictoryController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    public VictoryController(ProducerClient producer, AuthSessionService sessions) {
        this.producer = producer;
        this.sessions = sessions;
    }

    @GetMapping("/me/vittorie")
    public String victories(@RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        int p, s;
        try { p = Integer.parseInt(page); s = Integer.parseInt(size); }
        catch (NumberFormatException ex) { return error(response, model, HttpStatus.BAD_REQUEST, "Invalid page or size."); }
        if (p < 0 || s < 1 || s > 100) return error(response, model, HttpStatus.BAD_REQUEST, "Invalid page or size.");
        try {
            VictoryPage result = sessions.withAccess(request.getSession(false), token -> producer.victories(token, p, s));
            if (result == null || result.content() == null) throw new ProducerUnavailableException();
            model.addAttribute("victories", result);
            return "auction/victories";
        } catch (SessionExpiredException ex) { return "redirect:/login?expired=1"; }
        catch (ProducerUnavailableException ex) { return error(response, model, HttpStatus.SERVICE_UNAVAILABLE, "Victories are temporarily unavailable."); }
        catch (ProducerException ex) {
            return error(response, model, ex.status() == 403 ? HttpStatus.FORBIDDEN
                    : ex.status() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY,
                    ex.status() == 403 ? "Your account cannot view victories." : "Victories could not be loaded.");
        }
    }

    private static String error(HttpServletResponse response, Model model, HttpStatus status, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorMessage", message);
        return "auction/error";
    }
}
