package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

/** Le pagine principali si aprono e il form crea davvero un ordine. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "UTENTE")
class PagineTest {

  @Autowired
  private MockMvc mvc;

  @Test
  void elencoProdotti() throws Exception {
    mvc.perform(get("/prodotti")).andExpect(status().isOk())
        .andExpect(content().string(containsString("Trapano a batteria 18V")));
  }

  @Test
  void ilFormCreaUnOrdineEPortaAlDettaglio() throws Exception {
    mvc.perform(
        post("/ordini").with(csrf()).param("cliente", "Ferramenta Bianchi")
            .param("righe[0].codice", "GUA-L").param("righe[0].quantita", "2"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/ordini/*"));
  }

  @Test
  void ilFormMostraLErroreSeLaScortaNonBasta() throws Exception {
    mvc.perform(
        post("/ordini").with(csrf()).param("cliente", "Ferramenta Bianchi")
            .param("righe[0].codice", "NAS-50").param("righe[0].quantita", "1"))
        .andExpect(status().isOk()).andExpect(content()
            .string(containsString("Scorta insufficiente per NAS-50")));
  }

  @Test
  void ordineInesistente() throws Exception {
    mvc.perform(get("/ordini/9999")).andExpect(status().isNotFound());
  }

  @Test
  void dettaglioEdElencoMostranoScontiSpedizioneETotaleDaPagare()
      throws Exception {
    MvcResult creazione = mvc
        .perform(post("/ordini").with(csrf())
            .param("cliente", "Ferramenta Bianchi")
            .param("righe[0].codice", "GUA-L").param("righe[0].quantita", "10"))
        .andExpect(status().is3xxRedirection()).andReturn();
    String urlDettaglio = creazione.getResponse().getRedirectedUrl();

    mvc.perform(get(urlDettaglio)).andExpect(status().isOk())
        .andExpect(content().string(containsString("5%")))
        .andExpect(content().string(containsString("30,40 €")))
        .andExpect(content().string(containsString("Totale merce")))
        .andExpect(content().string(containsString("Spedizione")))
        .andExpect(content().string(containsString("9,90 €")))
        .andExpect(content().string(containsString("Totale da pagare")))
        .andExpect(content().string(containsString("40,30 €")));

    mvc.perform(get("/ordini")).andExpect(status().isOk())
        .andExpect(content().string(containsString("40,30 €")));
  }

  @Test
  void riassortimentoNonValidoMostraLErrore() throws Exception {
    mvc.perform(post("/prodotti/GUA-L/riassortimento").with(csrf()).param("quantita", "0"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("La quantità da riassortire va da 1 a 1000")));
  }

  @Test
  void riassortimentoValidoPortaAiMovimenti() throws Exception {
    mvc.perform(post("/prodotti/MET-5/riassortimento").with(csrf())
        .param("quantita", "10").param("nota", "Arrivo"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/movimenti"));
    mvc.perform(get("/movimenti")).andExpect(status().isOk())
        .andExpect(content().string(containsString("Arrivo")));
  }
}
