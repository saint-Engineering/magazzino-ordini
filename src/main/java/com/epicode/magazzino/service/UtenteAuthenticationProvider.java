package com.epicode.magazzino.service;

import com.epicode.magazzino.model.Utente;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Verifica le credenziali delegando al servizio il conteggio atomico degli errori. */
@Component
public class UtenteAuthenticationProvider implements AuthenticationProvider {

    private final UtenteService utenti;
    private final UserDetailsService userDetailsService;

    public UtenteAuthenticationProvider(UtenteService utenti, UserDetailsService userDetailsService) {
        this.utenti = utenti;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credenziale = authentication.getCredentials();
        String password = credenziale == null ? "" : credenziale.toString();
        Utente utente = utenti.autentica(username, password, indirizzoIp());
        UserDetails dettagli = userDetailsService.loadUserByUsername(utente.getUsername());
        UsernamePasswordAuthenticationToken autenticato =
                UsernamePasswordAuthenticationToken.authenticated(dettagli, null, dettagli.getAuthorities());
        autenticato.setDetails(authentication.getDetails());
        return autenticato;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private String indirizzoIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributi) {
            HttpServletRequest richiesta = attributi.getRequest();
            return richiesta.getRemoteAddr();
        }
        return "sconosciuto";
    }
}
