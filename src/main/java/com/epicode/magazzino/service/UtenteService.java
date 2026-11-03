package com.epicode.magazzino.service;

import com.epicode.magazzino.model.RuoloUtente;
import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.repository.UtenteRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casi d'uso transazionali per login e gestione degli account. */
@Service
@Transactional
public class UtenteService {

  private static final Logger LOG = LoggerFactory
      .getLogger(UtenteService.class);
  private static final Pattern PASSWORD_VALIDA = Pattern
      .compile("[\\x20-\\x7E]{8,128}");
  private static final Pattern CHIAVE_MCP_VALIDA = Pattern
      .compile("mcp_([A-Za-z0-9_-]{16})\\.([A-Za-z0-9_-]{43})");
  private static final SecureRandom GENERATORE_CASUALE = new SecureRandom();

  private final UtenteRepository utenti;
  private final PasswordEncoder passwordEncoder;

  public UtenteService(UtenteRepository utenti,
      PasswordEncoder passwordEncoder) {
    this.utenti = utenti;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(noRollbackFor = AuthenticationException.class)
  public Utente autentica(String username, String password,
      String indirizzoIp) {
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
    LOG.info("Login riuscito username={} timestamp={}",
        usernameSicuro(username), Instant.now());
    return utente;
  }

  @Transactional(readOnly = true)
  public List<Utente> elenco() {
    return utenti.findAllByOrderByUsernameAsc();
  }

  @Transactional(readOnly = true)
  public List<Utente> elencoBloccati() {
    return elenco().stream().filter(utente -> !utente.isAccountNonLocked())
        .toList();
  }

  @Transactional(readOnly = true)
  public Utente trova(Long id) {
    return utenti.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));
  }

  public UtenteConChiaveMcp crea(String username, String password, String nome,
      String cognome, int ruolo) {
    validaDatiUtente(username, nome, cognome, ruolo);
    validaPassword(password);
    if (utenti.existsByUsername(username)) {
      throw new IllegalArgumentException("Username già utilizzato");
    }
    Utente utente = utenti.save(new Utente(username,
        passwordEncoder.encode(password), nome, cognome, ruolo));
    String chiave = generaChiaveMcp(utente);
    return new UtenteConChiaveMcp(utente, chiave);
  }

  public String generaChiaveMcp(Long id) {
    return generaChiaveMcp(trova(id));
  }

  public void revocaChiaveMcp(Long id) {
    trova(id).revocaChiaveMcp();
  }

  @Transactional(readOnly = true)
  public Optional<Utente> autenticaChiaveMcp(String chiave) {
    if (chiave == null) {
      return Optional.empty();
    }
    Matcher matcher = CHIAVE_MCP_VALIDA.matcher(chiave);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    Utente utente = utenti.findByMcpKeyId(matcher.group(1)).orElse(null);
    if (utente == null) {
      return Optional.empty();
    }
    byte[] hashRicevuto = hashChiaveMcp(chiave);
    return utente.corrispondeHashChiaveMcp(hashRicevuto)
        ? Optional.of(utente)
        : Optional.empty();
  }

  private String generaChiaveMcp(Utente utente) {
    byte[] idCasuale = new byte[12];
    byte[] segretoCasuale = new byte[32];
    String id;
    do {
      GENERATORE_CASUALE.nextBytes(idCasuale);
      id = Base64.getUrlEncoder().withoutPadding().encodeToString(idCasuale);
    } while (utenti.existsByMcpKeyId(id));
    GENERATORE_CASUALE.nextBytes(segretoCasuale);
    String segreto = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(segretoCasuale);
    String chiave = "mcp_" + id + "." + segreto;
    utente.impostaChiaveMcp(id,
        HexFormat.of().formatHex(hashChiaveMcp(chiave)));
    return chiave;
  }

  private byte[] hashChiaveMcp(String chiave) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(chiave.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 non disponibile", e);
    }
  }

  public Utente modifica(Long id, String username, String nome, String cognome,
      int ruolo) {
    Utente utente = trova(id);
    validaDatiUtente(username, nome, cognome, ruolo);
    if (!utente.getUsername().equals(username)
        && utenti.existsByUsername(username)) {
      throw new IllegalArgumentException("Username già utilizzato");
    }
    if (utente.getRuolo() == RuoloUtente.AMMINISTRATORE.getValore()
        && ruolo != RuoloUtente.AMMINISTRATORE.getValore()
        && utenti
            .findAllByRuoloForUpdate(RuoloUtente.AMMINISTRATORE.getValore())
            .size() <= 1) {
      throw new IllegalStateException(
          "Non è possibile rimuovere l'ultimo amministratore");
    }
    utente.aggiornaDati(username, nome, cognome, ruolo);
    return utente;
  }

  public void elimina(Long id) {
    Utente utente = trova(id);
    if (utente.getRuolo() == RuoloUtente.AMMINISTRATORE.getValore()) {
      if (utenti.findAllByRuoloForUpdate(RuoloUtente.AMMINISTRATORE.getValore())
          .size() <= 1) {
        throw new IllegalStateException(
            "Non è possibile eliminare l'ultimo amministratore");
      }
    }
    utenti.delete(utente);
  }

  public void sblocca(Long id) {
    trova(id).azzeraErrori();
  }

  public void cambiaPassword(String username, String passwordAttuale,
      String nuovaPassword, String conferma) {
    Utente utente = utenti.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));
    if (!passwordEncoder.matches(passwordAttuale, utente.getPassword())) {
      throw new IllegalArgumentException("La password attuale non è corretta");
    }
    if (!nuovaPassword.equals(conferma)) {
      throw new IllegalArgumentException(
          "La nuova password e la conferma non coincidono");
    }
    validaPassword(nuovaPassword);
    utente.aggiornaPassword(passwordEncoder.encode(nuovaPassword));
  }

  private void validaDatiUtente(String username, String nome, String cognome,
      int ruolo) {
    if (username == null || username.isBlank() || username.length() > 100) {
      throw new IllegalArgumentException(
          "Username obbligatorio (massimo 100 caratteri)");
    }
    if (nome == null || nome.isBlank() || nome.length() > 100) {
      throw new IllegalArgumentException(
          "Nome obbligatorio (massimo 100 caratteri)");
    }
    if (cognome == null || cognome.isBlank() || cognome.length() > 100) {
      throw new IllegalArgumentException(
          "Cognome obbligatorio (massimo 100 caratteri)");
    }
    RuoloUtente.daValore(ruolo);
  }

  private void validaPassword(String password) {
    if (password == null || !PASSWORD_VALIDA.matcher(password).matches()) {
      throw new IllegalArgumentException(
          "La password deve contenere da 8 a 128 caratteri ASCII stampabili");
    }
  }

  private void registraTentativoFallito(String username, String indirizzoIp) {
    LOG.warn("Tentativo di accesso fallito username={} ip={} timestamp={}",
        usernameSicuro(username), indirizzoIp, Instant.now());
  }

  private String usernameSicuro(String username) {
    if (username == null) {
      return "";
    }
    return username.replaceAll("[\\p{Cntrl}]", "?");
  }
}
