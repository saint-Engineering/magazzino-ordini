package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epicode.magazzino.model.RuoloUtente;
import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.service.UtenteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "AMMINISTRATORE")
class UtentiBloccatiControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private UtenteService utenti;

  @Test
  void amministratoreVedeSoloAccountBloccatiConPulsanteIndividuale()
      throws Exception {
    Utente bloccato = creaUtente("bloccato-interfaccia", 3);
    creaUtente("attivo-interfaccia", 2);

    mvc.perform(get("/utenti/bloccati")).andExpect(status().isOk())
        .andExpect(content().string(containsString("bloccato-interfaccia")))
        .andExpect(content().string(not(containsString("attivo-interfaccia"))))
        .andExpect(content().string(containsString(
            "/utenti/bloccati/" + bloccato.getId() + "/sblocca")))
        .andExpect(content().string(containsString("Sblocca")))
        .andExpect(content().string(containsString("name=\"_csrf\"")))
        .andExpect(
            content().string(not(containsString(bloccato.getPassword()))));
    mvc.perform(get("/utenti")).andExpect(status().isOk()).andExpect(
        content().string(containsString("href=\"/utenti/bloccati\"")));
  }

  @Test
  void elencoVuotoMostraUnMessaggio() throws Exception {
    mvc.perform(get("/utenti/bloccati")).andExpect(status().isOk())
        .andExpect(content().string(containsString("Nessun utente bloccato")));
  }

  @Test
  void loSbloccoRiguardaSoloLUtenteSceltoERitornaAllaLista() throws Exception {
    Utente scelto = creaUtente("scelto-interfaccia", 3);
    Utente altro = creaUtente("altro-interfaccia", 4);

    mvc.perform(
        post("/utenti/bloccati/{id}/sblocca", scelto.getId()).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/utenti/bloccati"))
        .andExpect(flash().attribute("messaggio", "Utente sbloccato"));

    assertEquals(0, utenti.trova(scelto.getId()).getErrori());
    assertEquals(4, utenti.trova(altro.getId()).getErrori());
    mvc.perform(get("/utenti/bloccati")).andExpect(status().isOk())
        .andExpect(content().string(not(containsString("scelto-interfaccia"))))
        .andExpect(content().string(containsString("altro-interfaccia")));
  }

  @Test
  void sbloccoSenzaCsrfNonModificaAccount() throws Exception {
    Utente bloccato = creaUtente("csrf-interfaccia", 3);

    mvc.perform(post("/utenti/bloccati/{id}/sblocca", bloccato.getId()))
        .andExpect(status().isForbidden());

    assertFalse(utenti.trova(bloccato.getId()).isAccountNonLocked());
  }

  @Test
  @WithAnonymousUser
  void visitatoreDeveAutenticarsiPerListaESblocco() throws Exception {
    Utente bloccato = creaUtente("anonimo-interfaccia", 3);

    mvc.perform(get("/utenti/bloccati")).andExpect(status().is3xxRedirection());
    mvc.perform(
        post("/utenti/bloccati/{id}/sblocca", bloccato.getId()).with(csrf()))
        .andExpect(status().is3xxRedirection());

    assertEquals(3, utenti.trova(bloccato.getId()).getErrori());
  }

  @Test
  void utenteInesistenteMostraErroreSullaLista() throws Exception {
    mvc.perform(
        post("/utenti/bloccati/{id}/sblocca", Long.MAX_VALUE).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/utenti/bloccati"))
        .andExpect(flash().attribute("errore", "Utente non trovato"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"UTENTE", "MAGAZZINIERE"})
  void altriRuoliNonVedonoLaListaENonPossonoSbloccare(String ruolo)
      throws Exception {
    Utente bloccato = creaUtente("negato-interfaccia", 3);

    mvc.perform(get("/utenti/bloccati").with(user("operatore").roles(ruolo)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/utenti/bloccati/{id}/sblocca", bloccato.getId())
        .with(user("operatore").roles(ruolo)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/utenti/{id}/sblocca", bloccato.getId())
        .with(user("operatore").roles(ruolo)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/prodotti").with(user("operatore").roles(ruolo)))
        .andExpect(status().isOk()).andExpect(
            content().string(not(containsString("href=\"/utenti/bloccati\""))));

    assertEquals(3, utenti.trova(bloccato.getId()).getErrori());
  }

  @Test
  @WithMockUser(roles = "AMMINISTRATORE")
  void ilVecchioSbloccoContinuaARitornareAllElencoCompleto() throws Exception {
    Utente bloccato = creaUtente("compatibile-interfaccia", 3);

    mvc.perform(post("/utenti/{id}/sblocca", bloccato.getId()).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/utenti"));

    assertEquals(0, utenti.trova(bloccato.getId()).getErrori());
  }

  private Utente creaUtente(String username, int errori) {
    Utente utente = utenti.crea(username, "Password1!", "Nome", "Cognome",
        RuoloUtente.UTENTE.getValore()).utente();
    for (int i = 0; i < errori; i++) {
      utente.incrementaErrori();
    }
    return utente;
  }
}
