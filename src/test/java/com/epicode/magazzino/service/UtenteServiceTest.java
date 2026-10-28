package com.epicode.magazzino.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicode.magazzino.model.RuoloUtente;
import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.repository.UtenteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica regole di autenticazione, password e protezione degli
 * amministratori.
 */
@SpringBootTest
@Transactional
class UtenteServiceTest {

  @Autowired
  private UtenteService utenti;

  @Autowired
  private UtenteRepository repository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void terzoErroreBloccaAccountEIlMessaggioEQuelloPrevisto() {
    Utente utente = creaUtente("contatore");

    assertThrows(BadCredentialsException.class,
        () -> utenti.autentica("contatore", "Password2!", "127.0.0.1"));
    assertThrows(BadCredentialsException.class,
        () -> utenti.autentica("contatore", "Password2!", "127.0.0.1"));
    LockedException eccezione = assertThrows(LockedException.class,
        () -> utenti.autentica("contatore", "Password2!", "127.0.0.1"));

    assertEquals(3, utente.getErrori());
    assertEquals("Notifica inviata alla polizia postale",
        eccezione.getMessage());
    assertThrows(LockedException.class,
        () -> utenti.autentica("contatore", "Password1!", "127.0.0.1"));
  }

  @Test
  void autenticazioneRiuscitaAzzeraIlContatore() {
    Utente utente = creaUtente("reset");
    assertThrows(BadCredentialsException.class,
        () -> utenti.autentica("reset", "Password2!", "127.0.0.1"));

    utenti.autentica("reset", "Password1!", "127.0.0.1");

    assertEquals(0, utente.getErrori());
  }

  @Test
  void cambioPasswordValidaFormatoEConferma() {
    Utente utente = creaUtente("cambio");

    assertThrows(IllegalArgumentException.class,
        () -> utenti.cambiaPassword("cambio", "Password1!", "short", "short"));
    assertThrows(IllegalArgumentException.class, () -> utenti
        .cambiaPassword("cambio", "Password1!", "Password2!", "Password3!"));
    utenti.cambiaPassword("cambio", "Password1!", "Newpass2!", "Newpass2!");

    assertTrue(passwordEncoder.matches("Newpass2!", utente.getPassword()));
  }

  @Test
  void nonConsenteEliminazioneDellUltimoAmministratore() {
    Utente amministratore = repository.findByUsername("admin").orElseThrow();

    IllegalStateException eccezione = assertThrows(IllegalStateException.class,
        () -> utenti.elimina(amministratore.getId()));

    assertEquals("Non è possibile eliminare l'ultimo amministratore",
        eccezione.getMessage());
  }

  @Test
  void creaAccountConHashBCryptEImpedisceDuplicati() {
    Utente utente = creaUtente("nuovo");

    assertTrue(utente.getPassword().startsWith("$2"));
    assertTrue(passwordEncoder.matches("Password1!", utente.getPassword()));
    assertThrows(IllegalArgumentException.class, () -> utenti.crea("admin",
        "Password1!", "Nome", "Cognome", RuoloUtente.UTENTE.getValore()));
  }

  @Test
  void ruoloMagazziniereVieneTradottoNellAutoritaCorretta() {
    Utente magazziniere = utenti.crea("magazziniere", "Password1!", "Nome",
        "Cognome", RuoloUtente.MAGAZZINIERE.getValore());

    assertEquals("ROLE_MAGAZZINIERE",
        magazziniere.getAuthorities().iterator().next().getAuthority());
  }

  @Test
  void modificaSbloccaEImpedisceDiRimuovereUltimoAmministratore() {
    Utente utente = creaUtente("gestione");
    utenti.modifica(utente.getId(), "gestito", "Nuovo", "Nome",
        RuoloUtente.UTENTE.getValore());
    assertEquals("gestito", utente.getUsername());

    assertThrows(BadCredentialsException.class,
        () -> utenti.autentica("gestito", "Password2!", "127.0.0.1"));
    assertThrows(BadCredentialsException.class,
        () -> utenti.autentica("gestito", "Password2!", "127.0.0.1"));
    assertThrows(LockedException.class,
        () -> utenti.autentica("gestito", "Password2!", "127.0.0.1"));
    utenti.sblocca(utente.getId());
    assertEquals(0, utente.getErrori());
    utenti.autentica("gestito", "Password1!", "127.0.0.1");

    Utente amministratore = repository.findByUsername("admin").orElseThrow();
    assertThrows(IllegalStateException.class,
        () -> utenti.modifica(amministratore.getId(),
            amministratore.getUsername(), amministratore.getNome(),
            amministratore.getCognome(), RuoloUtente.UTENTE.getValore()));
  }

  private Utente creaUtente(String username) {
    return utenti.crea(username, "Password1!", "Nome", "Cognome",
        RuoloUtente.UTENTE.getValore());
  }
}
