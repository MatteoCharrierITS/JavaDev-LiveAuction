package it.esercitazione.liveauction.consumer.config;

import it.esercitazione.liveauction.consumer.auth.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    FilterRegistrationBean<SessionAuthenticationFilter> sessionFilterRegistration(SessionAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }


	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/login", "/registrazione", "/error", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/marketplace", "/prodotti/**", "/aste").permitAll()
						.requestMatchers(HttpMethod.POST, "/prodotti/*/acquisti").hasRole("USER")
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/impostazioni/portafoglio").denyAll()
						.requestMatchers("/aste/**", "/inventario/**", "/me/vittorie/**",
								"/impostazioni/portafoglio", "/impostazioni/portafoglio/**").hasRole("USER")
						.requestMatchers("/account", "/account/**").authenticated()
						// Keep this last so unknown public URLs reach MVC's accessible 404 page.
						// Add every new private GET route above it.
						.requestMatchers(HttpMethod.GET, "/**").permitAll()
						.anyRequest().authenticated())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.httpBasic(basic -> basic.disable())
				.exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) -> {
					String path = request.getRequestURI().substring(request.getContextPath().length());
					if (path.matches("^/aste/\\d+/(snapshot|wallet|ticket)$")) {
						response.setStatus(401);
						response.setContentType("application/json");
						response.setHeader("Cache-Control", "no-store");
						response.getWriter().write("{\"code\":\"SESSION_EXPIRED\"}");
						return;
					}
						if (Boolean.TRUE.equals(request.getAttribute("producerUnavailable"))) {
							response.sendRedirect("/login?unavailable=1");
						} else {
							response.sendRedirect("/login?expired=" + (Boolean.TRUE.equals(request.getAttribute("sessionExpired")) ? "1" : "0"));
						}
				}).accessDeniedHandler((request, response, ex) -> response.sendError(403)))
				.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
				.build();
	}

}
