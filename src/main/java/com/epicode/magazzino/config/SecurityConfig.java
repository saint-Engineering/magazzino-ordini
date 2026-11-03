package com.epicode.magazzino.config;

import com.epicode.magazzino.service.UtenteAuthenticationProvider;
import com.epicode.magazzino.service.UtenteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Configura login, sessione e autorizzazioni per le pagine dell'applicazione.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      UtenteAuthenticationProvider provider, UtenteService utenti)
      throws Exception {
    http.authenticationProvider(provider)
        .addFilterBefore(new McpApiKeyFilter(utenti), AuthorizationFilter.class)
        .csrf(csrf -> csrf.ignoringRequestMatchers("/mcp/user",
            "/mcp/magazzino", "/mcp/admin"))
        .authorizeHttpRequests(autorizzazioni -> autorizzazioni
            .requestMatchers("/login", "/css/**", "/error").permitAll()
            .requestMatchers("/mcp/user").hasRole("UTENTE")
            .requestMatchers("/mcp/magazzino").hasRole("MAGAZZINIERE")
            .requestMatchers("/mcp/admin").hasRole("AMMINISTRATORE")
            .requestMatchers("/utenti/**").hasRole("AMMINISTRATORE")
            .requestMatchers("/profilo/**").authenticated()
            .requestMatchers("/prodotti/nuovo", "/prodotti/*/riassortimento",
                "/prodotti/*/smaltimento")
            .hasRole("MAGAZZINIERE")
            .requestMatchers("/", "/prodotti/**", "/movimenti/**")
            .hasAnyRole("UTENTE", "MAGAZZINIERE")
            .requestMatchers("/ordini/**", "/report/**", "/scorte")
            .hasRole("UTENTE")
            .anyRequest().denyAll())
        .formLogin(
            login -> login.loginPage("/login").loginProcessingUrl("/login")
                .successHandler((richiesta, risposta, autenticazione) -> {
                  boolean amministratore = autenticazione.getAuthorities()
                      .stream().anyMatch(autorita -> autorita.getAuthority()
                          .equals("ROLE_AMMINISTRATORE"));
                  risposta
                      .sendRedirect(amministratore ? "/utenti" : "/prodotti");
                }).failureHandler((richiesta, risposta, errore) -> {
                  String parametro = errore instanceof LockedException
                      ? "bloccato"
                      : "errore";
                  risposta.sendRedirect("/login?" + parametro);
                }).permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
    return http.build();
  }
}
