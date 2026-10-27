package com.epicode.magazzino.service;

import com.epicode.magazzino.model.RuoloUtente;
import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.repository.UtenteRepository;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casi d'uso transazionali per login e gestione degli account. */
@Service
@Transactional
public class UtenteService {

    private static final Logger LOG = LoggerFactory.getLogger(UtenteService.class);
    private static final Pattern PASSWORD_VALIDA = Pattern.compile("[\\x20-\\x7E]{8,128}");

    private final UtenteRepository utenti;
    private final PasswordEncoder passwordEncoder;

    public UtenteService(UtenteRepository utenti, PasswordEncoder passwordEncoder) {
        this.utenti = utenti;
        this.passwordEncoder = passwordEncoder;
    }

    public Utente autentica(String username, String password, String indirizzoIp) {
        Utente utente = utenti.findByUsernameForUpdate(username).orElse(null);
        if (utente == null) {
            registraTentativoFallito(username, indirizzoIp);
            throw new BadCredentialsException("Autenticazione negata");
        }
        if (utente.getErrori() >= 3) {
            registraTentativoFallito(username, indirizzoIp);
            throw new LockedException("Notifica inviata alla polizia postale");
        }
        if (!passwordEncoder.matches(password, utente.getPassword())) {
            utente.incrementaErrori();
            utenti.save(utente);
            registraTentativoFallito(username, indirizzoIp);
            if (utente.getErrori() >= 3) {
                throw new LockedException("Notifica inviata alla polizia postale");
            }
            throw new BadCredentialsException("Autenticazione negata");
        }
        utente.azzeraErrori();
        LOG.info("Login riuscito username={} timestamp={}", usernameSicuro(username), Instant.now());
        return utente;
    }

    @Transactional(readOnly = true)
    public List<Utente> elenco() {
        return utenti.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public Utente trova(Long id) {
        return utenti.findById(id).orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));
    }

    public Utente crea(String username, String password, String nome, String cognome, int ruolo) {
        validaDatiUtente(username, nome, cognome, ruolo);
        validaPassword(password);
        if (utenti.existsByUsername(username)) {
            throw new IllegalArgumentException("Username già utilizzato");
        }
        return utenti.save(new Utente(username, passwordEncoder.encode(password), nome, cognome, ruolo));
    }

    public Utente modifica(Long id, String username, String nome, String cognome, int ruolo) {
        Utente utente = trova(id);
        validaDatiUtente(username, nome, cognome, ruolo);
        if (!utente.getUsername().equals(username) && utenti.existsByUsername(username)) {
            throw new IllegalArgumentException("Username già utilizzato");
        }
        utente.aggiornaDati(username, nome, cognome, ruolo);
        return utente;
    }

    public void elimina(Long id) {
        Utente utente = trova(id);
        if (utente.getRuolo() == RuoloUtente.AMMINISTRATORE.getValore()
                && utenti.countByRuolo(RuoloUtente.AMMINISTRATORE.getValore()) <= 1) {
            throw new IllegalStateException("Non è possibile eliminare l'ultimo amministratore");
        }
        utenti.delete(utente);
    }

    public void sblocca(Long id) {
        trova(id).azzeraErrori();
    }

    public void cambiaPassword(String username, String passwordAttuale, String nuovaPassword, String conferma) {
        Utente utente = utenti.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));
        if (!passwordEncoder.matches(passwordAttuale, utente.getPassword())) {
            throw new IllegalArgumentException("La password attuale non è corretta");
        }
        if (!nuovaPassword.equals(conferma)) {
            throw new IllegalArgumentException("La nuova password e la conferma non coincidono");
        }
        validaPassword(nuovaPassword);
        utente.aggiornaPassword(passwordEncoder.encode(nuovaPassword));
    }

    private void validaDatiUtente(String username, String nome, String cognome, int ruolo) {
        if (username == null || username.isBlank() || username.length() > 100) {
            throw new IllegalArgumentException("Username obbligatorio (massimo 100 caratteri)");
        }
        if (nome == null || nome.isBlank() || nome.length() > 100) {
            throw new IllegalArgumentException("Nome obbligatorio (massimo 100 caratteri)");
        }
        if (cognome == null || cognome.isBlank() || cognome.length() > 100) {
            throw new IllegalArgumentException("Cognome obbligatorio (massimo 100 caratteri)");
        }
        RuoloUtente.daValore(ruolo);
    }

    private void validaPassword(String password) {
        if (password == null || !PASSWORD_VALIDA.matcher(password).matches()) {
            throw new IllegalArgumentException("La password deve contenere da 8 a 128 caratteri ASCII stampabili");
        }
    }

    private void registraTentativoFallito(String username, String indirizzoIp) {
        LOG.warn(
                "Tentativo di accesso fallito username={} ip={} timestamp={}",
                usernameSicuro(username),
                indirizzoIp,
                Instant.now());
    }

    private String usernameSicuro(String username) {
        if (username == null) {
            return "";
        }
        return username.replaceAll("[\\p{Cntrl}]", "?");
    }
}
