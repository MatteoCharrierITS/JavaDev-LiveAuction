package it.esercitazione.liveauction.consumer.acquisto;

import it.esercitazione.liveauction.consumer.auth.*;
import it.esercitazione.liveauction.consumer.client.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller @RequiredArgsConstructor
public class PurchaseController {
    private final ProducerClient producer;
    private final AuthSessionService sessions;

    @PostMapping("/prodotti/{id}/acquisti")
    public String buy(@PathVariable long id,@RequestParam String clientPurchaseId,
                      @RequestParam String quantita,HttpServletRequest request,
                      HttpServletResponse response,Model model,RedirectAttributes flash) {
        response.setHeader("Cache-Control","no-store");
        UUID key; int quantity;
        try {
            key=UUID.fromString(clientPurchaseId); quantity=Integer.parseInt(quantita);
            if(id<1 || quantity<1 || quantity>100) throw new IllegalArgumentException();
        } catch(IllegalArgumentException ex) { return error(response,model,400,"Choose a quantity from 1 to 100 and reload the product.",id,null,null); }
        try {
            var result=sessions.withAccess(request.getSession(false),token->producer.buy(token,id,key,quantity));
            if(result==null || !result.id().equals(key) || result.prodottoId()!=id) throw new ProducerUnavailableException();
            flash.addFlashAttribute("notice",result.duplicato()?"Previous purchase confirmed: no second charge.":"Purchase completed. Products are now in your inventory.");
            return "redirect:/inventario";
        } catch(SessionExpiredException ex) {return "redirect:/login?expired=1";}
        catch(ProducerUnavailableException ex) {
            // Conservare UUID e quantità: un timeout può seguire un commit già avvenuto.
            return error(response,model,503,"Purchase outcome unknown. Retry this same request to avoid a second charge.",id,key,quantity);
        } catch(ProducerException ex) {
            String message=switch(ex.code()==null?"":ex.code()) {
                case "SALDO_INSUFFICIENTE" -> "Available balance is insufficient. Reserved bid funds cannot be spent.";
                case "PRODOTTO_NON_DISPONIBILE" -> "Not enough available stock remains.";
                case "PRODOTTO_NON_ACQUISTABILE" -> "This product has no fixed price.";
                case "CLIENT_PURCHASE_ID_GIA_UTILIZZATO" -> "Purchase identifier already used with different data. Reload the product.";
                default -> "Purchase could not be completed. Check the product and your balance.";
            };
            return error(response,model,ex.status(),message,id,null,null);
        }
    }
    private String error(HttpServletResponse response,Model model,int status,String message,long id,UUID key,Integer quantity) {
        response.setStatus(status); model.addAttribute("errorTitle","Purchase not completed");
        model.addAttribute("errorMessage",message); model.addAttribute("productId",id);
        model.addAttribute("retryId",key); model.addAttribute("retryQuantity",quantity);
        return "purchase/error";
    }
}
