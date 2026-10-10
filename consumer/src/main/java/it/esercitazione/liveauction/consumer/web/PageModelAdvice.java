package it.esercitazione.liveauction.consumer.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PageModelAdvice {
    @ModelAttribute("signedIn")
    public boolean signedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    @ModelAttribute("displayName")
    public String displayName(Authentication authentication) {
        return signedIn(authentication) ? authentication.getName() : null;
    }

    @ModelAttribute("userVisitor")
    public boolean userVisitor(Authentication authentication) {
        return signedIn(authentication) && authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
