package it.esercitazione.liveauction.consumer.admin;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.auth.SessionExpiredException;
import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    public AdminController(ProducerClient producer, AuthSessionService sessions) {
        this.producer = producer;
        this.sessions = sessions;
    }

    @GetMapping("/prodotti")
    public String products(@RequestParam(required = false) String query,
                           @RequestParam(required = false) String categoria,
                           @RequestParam(required = false) Boolean astabile,
                           @RequestParam(required = false) Boolean attivo,
                           @RequestParam(defaultValue = "0") String page,
                           @RequestParam(defaultValue = "12") String size,
                           HttpServletRequest request, HttpServletResponse response, Model model) {
        int[] bounds = pagination(page, size);
        if (bounds == null) return error(response, model, HttpStatus.BAD_REQUEST, "Invalid page or size.");
        try {
            var categories = access(request, token -> producer.adminCategories(token));
            var products = access(request, token -> producer.adminProducts(token, clean(query), clean(categoria),
                    astabile, attivo, bounds[0], bounds[1]));
            if (categories == null || products == null || products.content() == null) throw new ProducerUnavailableException();
            model.addAttribute("categories", categories);
            model.addAttribute("products", products);
            model.addAttribute("query", clean(query));
            model.addAttribute("categoria", clean(categoria));
            model.addAttribute("astabile", astabile);
            model.addAttribute("attivo", attivo);
            return "admin/products";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) { return producerError(response, model, ex); }
    }

    @GetMapping("/categorie")
    public String categories(HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            var list = access(request, token -> producer.adminCategories(token));
            if (list == null) throw new ProducerUnavailableException();
            model.addAttribute("categories", list);
            if (!model.containsAttribute("form")) model.addAttribute("form", new CategoryForm("", "", true));
            return "admin/categories";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) { return producerError(response, model, ex); }
    }

    @GetMapping("/categorie/{id}/modifica")
    public String editCategory(@PathVariable long id, HttpServletRequest request,
                               HttpServletResponse response, Model model) {
        try {
            var categories = access(request, token -> producer.adminCategories(token));
            if (categories == null) throw new ProducerUnavailableException();
            for (var category : categories) {
                if (category.id() != null && category.id() == id) {
                    model.addAttribute("category", category);
                    model.addAttribute("categoryId", id);
                    model.addAttribute("form", CategoryForm.from(category));
                    return "admin/category-form";
                }
            }
            return error(response, model, HttpStatus.NOT_FOUND, "Category not found.");
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) { return producerError(response, model, ex); }
    }

    @PostMapping("/categorie")
    public String createCategory(@Valid @ModelAttribute("form") CategoryForm form, BindingResult result,
                                 HttpServletRequest request, HttpServletResponse response,
                                 Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("formError", "Check the category name and slug.");
            return categories(request, response, model);
        }
        try {
            access(request, token -> producer.saveAdminCategory(token, null, form));
            flash.addFlashAttribute("notice", "Category created.");
            return "redirect:/admin/categorie";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) {
            if (ex.status() == 409 || ex.status() == 400) {
                response.setStatus(ex.status());
                model.addAttribute("formError", ex.status() == 409
                        ? "Category name or slug is already in use." : "The Producer rejected this category.");
                return categories(request, response, model);
            }
            return producerError(response, model, ex);
        }
    }

    @PostMapping("/categorie/{id}")
    public String updateCategory(@PathVariable long id, @Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult result, HttpServletRequest request, HttpServletResponse response,
                                 Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("formError", "Check the category name and slug.");
            model.addAttribute("categoryId", id);
            return "admin/category-form";
        }
        try {
            access(request, token -> producer.saveAdminCategory(token, id, form));
            flash.addFlashAttribute("notice", "Category updated.");
            return "redirect:/admin/categorie";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) {
            if (ex.status() == 409 || ex.status() == 400) {
                response.setStatus(ex.status());
                model.addAttribute("formError", ex.status() == 409
                        ? "Category name or slug is already in use." : "The Producer rejected this category.");
                model.addAttribute("categoryId", id);
                return "admin/category-form";
            }
            return producerError(response, model, ex);
        }
    }

    @GetMapping("/prodotti/nuovo")
    public String newProduct(HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("form", ProductForm.empty());
        return productForm(request, response, model, null);
    }

    @GetMapping("/prodotti/{id}/modifica")
    public String editProduct(@PathVariable long id, HttpServletRequest request,
                              HttpServletResponse response, Model model) {
        try {
            var product = access(request, token -> producer.adminProduct(token, id));
            if (product == null) throw new ProducerUnavailableException();
            model.addAttribute("form", ProductForm.from(product));
            model.addAttribute("product", product);
            return productForm(request, response, model, id);
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) { return producerError(response, model, ex); }
    }

    @PostMapping("/prodotti")
    public String createProduct(@Valid @ModelAttribute("form") ProductForm form, BindingResult result,
                                HttpServletRequest request, HttpServletResponse response,
                                Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("formError", "Check the product fields.");
            return productForm(request, response, model, null);
        }
        try {
            access(request, token -> producer.saveAdminProduct(token, null, form));
            flash.addFlashAttribute("notice", "Product created.");
            return "redirect:/admin/prodotti";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) {
            if (ex.status() == 400 || ex.status() == 409) {
                response.setStatus(ex.status());
                model.addAttribute("formError", productError(ex));
                return productForm(request, response, model, null);
            }
            return producerError(response, model, ex);
        }
    }

    @PostMapping("/prodotti/{id}")
    public String updateProduct(@PathVariable long id, @Valid @ModelAttribute("form") ProductForm form,
                                BindingResult result, HttpServletRequest request, HttpServletResponse response,
                                Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("formError", "Check the product fields.");
            return productForm(request, response, model, id);
        }
        try {
            access(request, token -> producer.saveAdminProduct(token, id, form));
            flash.addFlashAttribute("notice", "Product updated.");
            return "redirect:/admin/prodotti";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) {
            if (ex.status() == 409 && "VERSIONE_NON_AGGIORNATA".equals(ex.code())) {
                try {
                    var current = access(request, token -> producer.adminProduct(token, id));
                    if (current == null) throw new ProducerUnavailableException();
                    response.setStatus(409);
                    model.addAttribute("attempted", form);
                    model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + "form");
                    model.addAttribute("form", ProductForm.from(current));
                    model.addAttribute("product", current);
                    model.addAttribute("formError", "This product changed. Review the current values and your attempted changes before submitting again.");
                    return productForm(request, response, model, id);
                } catch (SessionExpiredException expired) { return expired(); }
                catch (ProducerUnavailableException unavailable) { return unavailable(response, model); }
                catch (ProducerException readError) { return producerError(response, model, readError); }
            }
            if (ex.status() == 400 || ex.status() == 409) {
                response.setStatus(ex.status());
                model.addAttribute("formError", productError(ex));
                return productForm(request, response, model, id);
            }
            return producerError(response, model, ex);
        }
    }

    @GetMapping("/aste/nuova")
    public String newAuction(@RequestParam(required = false) Long prodottoId,
                             HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("form", new AuctionForm(prodottoId, null, null));
        return auctionForm(request, response, model);
    }

    @PostMapping("/aste")
    public String schedule(@Valid @ModelAttribute("form") AuctionForm form, BindingResult result,
                           HttpServletRequest request, HttpServletResponse response,
                           Model model, RedirectAttributes flash) {
        if (result.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("formError", "Choose a product, a local start time, and a positive price with at most two decimal places.");
            return auctionForm(request, response, model);
        }
        try {
            var created = access(request, token -> producer.scheduleAuction(token, form));
            if (created == null || created.id() == null) throw new ProducerUnavailableException();
            flash.addFlashAttribute("notice", "Auction #" + created.id() + " scheduled. The Producer reserved one unit.");
            return "redirect:/aste";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) {
            if (ex.status() == 400 || ex.status() == 404 || ex.status() == 409 || ex.status() == 422) {
                response.setStatus(ex.status());
                model.addAttribute("formError", auctionError(ex));
                return auctionForm(request, response, model);
            }
            return producerError(response, model, ex);
        }
    }

    private String productForm(HttpServletRequest request, HttpServletResponse response, Model model, Long id) {
        try {
            var categories = access(request, token -> producer.adminCategories(token));
            if (categories == null) throw new ProducerUnavailableException();
            model.addAttribute("categories", categories);
            model.addAttribute("productId", id);
            return "admin/product-form";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) { return producerError(response, model, ex); }
    }

    private String auctionForm(HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            List<AdminProduct> eligible = new ArrayList<>();
            int page = 0;
            AdminProductPage batch;
            do {
                int currentPage = page;
                batch = access(request, token -> producer.adminProducts(token, null, null, true, null, currentPage, 100));
                if (batch == null || batch.content() == null) throw new ProducerUnavailableException();
                batch.content().stream().filter(AdminProduct::eligible).forEach(eligible::add);
                page++;
            } while (page < batch.totalPages());
            model.addAttribute("eligibleProducts", eligible);
            return "admin/auction-form";
        } catch (SessionExpiredException ex) { return expired(); }
        catch (ProducerUnavailableException ex) { return unavailable(response, model); }
        catch (ProducerException ex) { return producerError(response, model, ex); }
    }

    private <T> T access(HttpServletRequest request, java.util.function.Function<String, T> operation) {
        return sessions.withAccess(request.getSession(false), operation);
    }

    private static String productError(ProducerException ex) {
        if ("SKU_GIA_UTILIZZATO".equals(ex.code())) return "This SKU is already in use.";
        if ("RISORSA_NON_TROVATA".equals(ex.code())) return "The selected category no longer exists.";
        return "The Producer rejected this product. Review all fields and try again.";
    }

    private static String auctionError(ProducerException ex) {
        return switch (ex.code() == null ? "" : ex.code()) {
            case "PRODOTTO_NON_DISPONIBILE" -> "No stock remains for this product. Choose another product.";
            case "PRODOTTO_NON_ASTABILE" -> "This product is no longer eligible for an auction.";
            case "DATA_INIZIO_NON_VALIDA" -> "The start time is invalid in Europe/Rome. Choose another time.";
            case "PREZZO_INIZIALE_NON_VALIDO" -> "The starting price is invalid. Enter a positive amount.";
            case "RISORSA_NON_TROVATA" -> "This product no longer exists.";
            default -> "The Producer rejected this schedule. Review the product, time, and price.";
        };
    }

    private static int[] pagination(String page, String size) {
        try {
            int p = Integer.parseInt(page), s = Integer.parseInt(size);
            return p >= 0 && s >= 1 && s <= 100 ? new int[]{p, s} : null;
        } catch (NumberFormatException ex) { return null; }
    }

    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static String expired() { return "redirect:/login?expired=1"; }
    private static String unavailable(HttpServletResponse response, Model model) {
        return error(response, model, HttpStatus.SERVICE_UNAVAILABLE, "The Producer is temporarily unavailable. Please try again.");
    }
    private static String producerError(HttpServletResponse response, Model model, ProducerException ex) {
        HttpStatus status = switch (ex.status()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return error(response, model, status, switch (status) {
            case BAD_REQUEST -> "The Producer rejected this request.";
            case FORBIDDEN -> "Your account cannot manage this catalogue.";
            case NOT_FOUND -> "This resource no longer exists.";
            default -> "The Producer could not complete this request.";
        });
    }
    private static String error(HttpServletResponse response, Model model, HttpStatus status, String message) {
        response.setStatus(status.value());
        model.addAttribute("errorMessage", message);
        return "admin/error";
    }
}
