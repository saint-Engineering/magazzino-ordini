package com.epicode.magazzino.config;

import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.service.UtenteService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Autentica esclusivamente l'endpoint MCP tramite chiave Bearer personale. */
final class McpApiKeyFilter extends OncePerRequestFilter {

  private static final Set<String> ENDPOINT = Set.of("/mcp/user",
      "/mcp/magazzino", "/mcp/admin");

  private final UtenteService utenti;

  McpApiKeyFilter(UtenteService utenti) {
    this.utenti = utenti;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest richiesta) {
    String percorso = richiesta.getRequestURI()
        .substring(richiesta.getContextPath().length());
    return !ENDPOINT.contains(percorso);
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest richiesta,
      HttpServletResponse risposta, FilterChain catena)
      throws ServletException, IOException {
    String intestazione = richiesta.getHeader("Authorization");
    if (intestazione == null
        || !intestazione.regionMatches(true, 0, "Bearer ", 0, 7)) {
      nonAutorizzato(risposta);
      return;
    }

    Utente utente = utenti.autenticaChiaveMcp(intestazione.substring(7))
        .orElse(null);
    if (utente == null) {
      nonAutorizzato(risposta);
      return;
    }

    UsernamePasswordAuthenticationToken autenticazione = UsernamePasswordAuthenticationToken
        .authenticated(utente, null, utente.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(autenticazione);
    catena.doFilter(richiesta, risposta);
  }

  private void nonAutorizzato(HttpServletResponse risposta) {
    risposta.setHeader("WWW-Authenticate", "Bearer");
    risposta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }
}
