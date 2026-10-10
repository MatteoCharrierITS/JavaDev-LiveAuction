package it.esercitazione.liveauction.consumer.auth;

import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import it.esercitazione.liveauction.consumer.dto.RegistrationRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthWebController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    public AuthWebController(ProducerClient producer, AuthSessionService sessions) {
        this.producer = producer;
        this.sessions = sessions;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        return authenticated(authentication) ? "redirect:/account" : "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpServletRequest request,
                            @RequestParam(required = false) String registered,
                            @RequestParam(required = false) String expired,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String deleted,
                            @RequestParam(required = false) String unavailable) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new LoginForm());
        if ("1".equals(registered)) model.addAttribute("notice", "Account created. Please sign in.");
        if ("1".equals(expired) || Boolean.TRUE.equals(request.getAttribute("sessionExpired")))
            model.addAttribute("error", "Your session expired. Please sign in again.");
        if ("1".equals(logout)) model.addAttribute("notice", "You have signed out.");
        if ("1".equals(deleted)) model.addAttribute("notice", "Your account has been deleted.");
        if ("1".equals(unavailable) || Boolean.TRUE.equals(request.getAttribute("producerUnavailable")))
            model.addAttribute("error", "The service is temporarily unavailable. Please try again.");
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("form") LoginForm form, BindingResult validation,
                        HttpServletRequest request, Model model) {
        if (validation.hasErrors()) return "auth/login";
        try {
            request.getSession();
            request.changeSessionId();
            sessions.login(request.getSession(), form.getUsername(), form.getPassword());
            return "redirect:/account";
        } catch (ProducerException ex) {
            model.addAttribute("error", ex.status() == 401 ? "Invalid username or password."
                    : "Sign in failed. Please check your details and try again.");
        } catch (ProducerUnavailableException ex) {
            model.addAttribute("error", "The service is temporarily unavailable. Please try again.");
        }
        form.setPassword(null);
        return "auth/login";
    }

    @GetMapping("/registrazione")
    public String registrationPage(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new RegistrationForm());
        return "auth/registration";
    }

    @PostMapping("/registrazione")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult validation,
                           Model model) {
        if (validation.hasErrors()) return "auth/registration";
        try {
            producer.register(new RegistrationRequest(form.getUsername(), form.getEmail(), form.getPassword()));
            return "redirect:/login?registered=1";
        } catch (ProducerException ex) {
            model.addAttribute("error", ex.status() == 409 ? "Username or email is already in use."
                    : "Registration failed. Please check your details and try again.");
        } catch (ProducerUnavailableException ex) {
            model.addAttribute("error", "The service is temporarily unavailable. Please try again.");
        }
        form.setPassword(null);
        return "auth/registration";
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "auth/account";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, RedirectAttributes flash) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                sessions.withAccess(session, token -> { producer.logout(token); return null; });
            } catch (ProducerUnavailableException ex) {
                flash.addFlashAttribute("error", "The service could not confirm sign out. Your browser session has been cleared.");
            } catch (ProducerException | SessionExpiredException ex) {
                // An unusable Producer session must not keep the Consumer session alive.
            } finally {
                sessions.clear(session);
            }
        }
        return "redirect:/login?logout=1";
    }

    @PostMapping("/account/delete")
    public String deleteAccount(HttpServletRequest request, RedirectAttributes flash) {
        HttpSession session = request.getSession(false);
        try {
            sessions.withAccess(session, token -> { producer.deleteAccount(token); return null; });
            return "redirect:/login?deleted=1";
        } catch (ProducerUnavailableException ex) {
            flash.addFlashAttribute("error", "Account deletion could not be confirmed. Please sign in again to check.");
        } catch (ProducerException | SessionExpiredException ex) {
            flash.addFlashAttribute("error", "Account deletion could not be completed. Please sign in again.");
        } finally {
            if (session != null) sessions.clear(session);
        }
        return "redirect:/login";
    }

    private boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}
