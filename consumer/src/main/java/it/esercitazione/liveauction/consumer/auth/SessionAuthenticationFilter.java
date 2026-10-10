package it.esercitazione.liveauction.consumer.auth;

import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final AuthSessionService sessions;

    public SessionAuthenticationFilter(AuthSessionService sessions) { this.sessions = sessions; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                SessionAuth auth = sessions.current(session);
                if (auth != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(auth.username(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + auth.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (SessionExpiredException ex) {
                request.setAttribute("sessionExpired", true);
            } catch (IllegalStateException ex) {
                request.setAttribute("sessionExpired", true);
            } catch (ProducerUnavailableException ex) {
                request.setAttribute("producerUnavailable", true);
            }
        }
        chain.doFilter(request, response);
    }
}
