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
						.requestMatchers("/", "/login", "/registrazione", "/error", "/css/**", "/images/**", "/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/marketplace", "/prodotti/**", "/aste").permitAll()
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/aste/**", "/inventario/**", "/me/vittorie/**",
								"/impostazioni/portafoglio", "/impostazioni/portafoglio/**").hasRole("USER")
						.anyRequest().authenticated())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.httpBasic(basic -> basic.disable())
				.exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) -> {
						if (Boolean.TRUE.equals(request.getAttribute("producerUnavailable"))) {
							response.sendRedirect("/login?unavailable=1");
						} else {
							response.sendRedirect("/login?expired=" + (Boolean.TRUE.equals(request.getAttribute("sessionExpired")) ? "1" : "0"));
						}
				}))
				.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
				.build();
	}

}
