package com.epicode.magazzino.service;

import com.epicode.magazzino.model.Utente;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Verifica le credenziali delegando al servizio il conteggio atomico degli
 * errori.
 */
@Component
public class UtenteAuthenticationProvider implements AuthenticationProvider {

  private final UtenteService utenti;

  public UtenteAuthenticationProvider(UtenteService utenti) {
    this.utenti = utenti;
  }

  @Override
  public Authentication authenticate(Authentication authentication)
      throws AuthenticationException {
    String username = authentication.getName();
    Object credenziale = authentication.getCredentials();
    String password = credenziale == null ? "" : credenziale.toString();
    Utente utente = utenti.autentica(username, password, indirizzoIp());
    UsernamePasswordAuthenticationToken autenticato = UsernamePasswordAuthenticationToken
        .authenticated(utente, null, utente.getAuthorities());
    autenticato.setDetails(authentication.getDetails());
    return autenticato;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class
        .isAssignableFrom(authentication);
  }

  private String indirizzoIp() {
    if (RequestContextHolder
        .getRequestAttributes() instanceof ServletRequestAttributes attributi) {
      HttpServletRequest richiesta = attributi.getRequest();
      return richiesta.getRemoteAddr();
    }
    return "sconosciuto";
  }
}
