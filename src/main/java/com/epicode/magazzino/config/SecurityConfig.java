package com.epicode.magazzino.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.epicode.magazzino.service.UtenteAuthenticationProvider;

/**
 * Configura login, sessione e autorizzazioni per le pagine dell'applicazione.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      UtenteAuthenticationProvider provider) throws Exception {
    http.authenticationProvider(provider)
        .authorizeHttpRequests(autorizzazioni -> autorizzazioni
            .requestMatchers("/login", "/css/**", "/error").permitAll()
            .requestMatchers("/utenti/**").hasRole("AMMINISTRATORE")
            .requestMatchers("/profilo/**").authenticated()
            .requestMatchers("/prodotti/nuovo", "/prodotti/*/riassortimento",
                "/prodotti/*/smaltimento").hasRole("MAGAZZINIERE")
            .requestMatchers("/", "/prodotti/**", "/movimenti/**")
            .hasAnyRole("UTENTE", "MAGAZZINIERE")
            .requestMatchers("/ordini/**", "/report/**").hasRole("UTENTE")
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
