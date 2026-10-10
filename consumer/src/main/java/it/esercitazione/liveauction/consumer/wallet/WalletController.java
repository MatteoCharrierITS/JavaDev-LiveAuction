package it.esercitazione.liveauction.consumer.wallet;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class WalletController {
    private static final int DEFAULT_SIZE = 20;
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    public WalletController(ProducerClient producer, AuthSessionService sessions) {
        this.producer = producer;
        this.sessions = sessions;
    }

    @GetMapping("/impostazioni/portafoglio")
    public String wallet(@RequestParam(defaultValue = "0") String page,
                         @RequestParam(defaultValue = "20") String size,
                         HttpServletRequest request, HttpServletResponse response, Model model) {
        int[] pagination = pagination(page, size);
        if (pagination == null) return error(model, response, HttpStatus.BAD_REQUEST,
                "Invalid page", "Choose a page of zero or greater and a page size from 1 to 100.");
        return load(request, response, model, pagination[0], pagination[1]);
    }

    @PostMapping("/impostazioni/portafoglio")
    public String setBalance(@RequestParam(required = false) String saldoTotale,
                             @RequestParam(defaultValue = "0") String page,
                             @RequestParam(defaultValue = "20") String size,
                             HttpServletRequest request, HttpServletResponse response,
                             Model model, RedirectAttributes flash) {
        int[] pagination = pagination(page, size);
        if (pagination == null) return error(model, response, HttpStatus.BAD_REQUEST,
                "Invalid page", "Choose a page of zero or greater and a page size from 1 to 100.");
        BigDecimal total;
        try {
            total = saldoTotale == null ? null : new BigDecimal(saldoTotale.trim());
            if (total == null || total.signum() < 0 || total.scale() > 2
                    || total.compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw new NumberFormatException("Invalid balance");
            }
        } catch (NumberFormatException ex) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("formError", "Enter an amount from 0 to 9,999,999,999.99 with at most two decimal places.");
            model.addAttribute("enteredBalance", saldoTotale);
            return load(request, response, model, pagination[0], pagination[1]);
        }
        try {
            WalletBalance balance = sessions.withAccess(request.getSession(false),
                    token -> producer.setWalletBalance(token, total));
            if (balance == null) throw new ProducerUnavailableException();
            flash.addFlashAttribute("notice", "Virtual balance set. The balances below are current Producer values.");
            return "redirect:/impostazioni/portafoglio?page=" + pagination[0] + "&size=" + pagination[1];
        } catch (SessionExpiredException ex) {
            return "redirect:/login?expired=1";
        } catch (ProducerException ex) {
            if (ex.status() == 400) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                model.addAttribute("formError", "The Producer rejected this amount. Check the allowed range and decimal places.");
            } else if (ex.status() == 409 && "SALDO_INFERIORE_AL_RISERVATO".equals(ex.code())) {
                response.setStatus(HttpStatus.CONFLICT.value());
                model.addAttribute("formError", "The new total cannot be lower than the currently reserved balance.");
            } else if (ex.status() == 403) {
                return error(model, response, HttpStatus.FORBIDDEN,
                        "Access denied", "Your account cannot change this wallet.");
            } else if (ex.status() == 404) {
                return error(model, response, HttpStatus.NOT_FOUND,
                        "Wallet missing", "Your wallet could not be found.");
            } else {
                return error(model, response, HttpStatus.BAD_GATEWAY,
                        "Wallet unavailable", "The wallet could not be updated. Please try again.");
            }
            model.addAttribute("enteredBalance", saldoTotale);
            return load(request, response, model, pagination[0], pagination[1]);
        } catch (ProducerUnavailableException ex) {
            return error(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Wallet unavailable", "The wallet service is temporarily unavailable. Please try again.");
        }
    }

    private String load(HttpServletRequest request, HttpServletResponse response, Model model,
                        int page, int size) {
        try {
            WalletPage wallet = sessions.withAccess(request.getSession(false),
                    token -> producer.wallet(token, page, size));
            if (wallet == null || wallet.movimenti() == null || wallet.saldoTotale() == null
                    || wallet.saldoRiservato() == null || wallet.saldoDisponibile() == null
                    || wallet.valuta() == null) throw new ProducerUnavailableException();
            model.addAttribute("wallet", wallet);
            model.addAttribute("display", new WalletDisplay());
            if (!model.containsAttribute("enteredBalance"))
                model.addAttribute("enteredBalance", wallet.saldoTotale().toPlainString());
            return "wallet/page";
        } catch (SessionExpiredException ex) {
            return "redirect:/login?expired=1";
        } catch (ProducerException ex) {
            return switch (ex.status()) {
                case 400 -> error(model, response, HttpStatus.BAD_REQUEST,
                        "Invalid page", "The Producer rejected the wallet page request.");
                case 403 -> error(model, response, HttpStatus.FORBIDDEN,
                        "Access denied", "Your account cannot view this wallet.");
                case 404 -> error(model, response, HttpStatus.NOT_FOUND,
                        "Wallet missing", "Your wallet could not be found.");
                default -> error(model, response, HttpStatus.BAD_GATEWAY,
                        "Wallet unavailable", "The wallet could not be loaded. Please try again.");
            };
        } catch (ProducerUnavailableException ex) {
            return error(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Wallet unavailable", "The wallet service is temporarily unavailable. Please try again.");
        }
    }

    private static int[] pagination(String page, String size) {
        try {
            int number = Integer.parseInt(page);
            int count = Integer.parseInt(size);
            return number >= 0 && count >= 1 && count <= 100 ? new int[]{number, count} : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String error(Model model, HttpServletResponse response, HttpStatus status,
                                String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorTitle", title);
        model.addAttribute("errorMessage", message);
        return "wallet/error";
    }
}
