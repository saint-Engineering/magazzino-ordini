package com.epicode.magazzino.service;

import com.epicode.magazzino.repository.UtenteRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Carica dal repository il principal richiesto dai componenti Spring Security. */
@Service
public class UtenteDetailsService implements UserDetailsService {

    private final UtenteRepository utenti;

    public UtenteDetailsService(UtenteRepository utenti) {
        this.utenti = utenti;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return utenti.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato"));
    }
}
