package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.repository.OrdineRepository;
import com.epicode.magazzino.service.OrdineService;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "UTENTE")
class ReportControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private OrdineRepository ordini;

  @Autowired
  private OrdineService ordineService;

  @Autowired
  private Clock clock;

  @Test
  void reportOrdiniMostraSoloGliOrdiniDelClienteRichiesto() throws Exception {
    Ordine ordineRichiesto = ordini.save(new Ordine("Ferramenta Report",
        LocalDateTime.of(2026, 10, 28, 10, 0)));
    Ordine altroOrdine = ordini.save(new Ordine("Cliente Diverso",
        LocalDateTime.of(2026, 10, 28, 11, 0)));

    try {
      mvc.perform(get("/report/ordini").param("cliente", "Ferramenta Report"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("Ferramenta Report")))
          .andExpect(content().string(not(containsString("Cliente Diverso"))));
    } finally {
      ordini.deleteAllById(List.of(ordineRichiesto.getId(), altroOrdine.getId()));
    }
  }

  @Test
  void reportOrdiniMostraIlNomeOrdinarioNelTitolo() throws Exception {
    mvc.perform(get("/report/ordini").param("cliente", "Ferramenta Bianchi"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Ordini di")))
        .andExpect(content().string(containsString("Ferramenta Bianchi")));
  }

  @Test
  void reportOrdiniNonMostraOrdiniSpeditiConClienteSqlInjection() throws Exception {
    Ordine ordine = ordini.save(new Ordine("Cliente Report",
        LocalDateTime.of(2026, 10, 28, 12, 0)));

    try {
      ordineService.spedisci(ordine.getId(), "IT123456789IT");

      mvc.perform(get("/report/ordini").param("cliente", "Cliente Report"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsStringIgnoringCase("spedito")));

      mvc.perform(get("/report/ordini").param("cliente", "' or 1=1; --"))
          .andExpect(status().isOk())
          .andExpect(content().string(not(containsStringIgnoringCase("spedito"))));
    } finally {
      ordini.deleteById(ordine.getId());
    }
  }

  @Test
  void intestazioneCodificaCaratteriHtmlNelModelloUrlEncoded() throws Exception {
    verificaIntestazioneCodificata(get(URI.create(
        "/report/intestazione?modello=%3Cbutton%20onClick=%22window.open(%27https://www.google.com%27)"
            + "%22%3EClick%20%26%20Me%3C/button%3E")));
  }

  @Test
  void intestazioneCodificaCaratteriHtmlNelModelloInTestoNormale() throws Exception {
    verificaIntestazioneCodificata(get("/report/intestazione").param("modello",
        "<button onClick=\"window.open('https://www.google.com')\">Click & Me</button>"));
  }

  @Test
  void intestazioneSostituisceSoloIlSegnapostoDataConsentito() throws Exception {
    String annoCorrente = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy"));

    mvc.perform(get("/report/intestazione")
            .param("modello", "Report ${date:yyyy} ${env:PATH}"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Report " + annoCorrente + " ${env:PATH}")));
  }

  private void verificaIntestazioneCodificata(MockHttpServletRequestBuilder richiesta)
      throws Exception {
    mvc.perform(richiesta)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpectAll(
            content().string(not(containsString("<"))),
            content().string(not(containsString(">"))),
            content().string(containsString("&#x26;")),
            content().string(containsString("&#x3c;")),
            content().string(containsString("&#x3e;")),
            content().string(containsString("&#x22;")),
            content().string(containsString("&#x27;")));
  }
}
