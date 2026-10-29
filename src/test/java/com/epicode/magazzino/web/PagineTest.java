package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
  void spedireRichiedeIlCodiceEloMostraNelDettaglio() throws Exception {
    MvcResult creazione = mvc
        .perform(post("/ordini").with(csrf())
            .param("cliente", "Ferramenta Bianchi")
            .param("righe[0].codice", "GUA-L").param("righe[0].quantita", "1"))
        .andExpect(status().is3xxRedirection()).andReturn();
    String urlDettaglio = creazione.getResponse().getRedirectedUrl();

    mvc.perform(post(urlDettaglio + "/spedisci").with(csrf())
        .param("codiceTracking", "IT123456789IT"))
        .andExpect(status().is3xxRedirection());

    mvc.perform(get(urlDettaglio)).andExpect(status().isOk())
        .andExpect(content().string(containsString("IT123456789IT")));

    mvc.perform(get("/ordini")).andExpect(status().isOk())
        .andExpect(content().string(not(containsString("IT123456789IT"))));
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
  @WithMockUser(roles = "MAGAZZINIERE")
  void riassortimentoNonValidoMostraLErrore() throws Exception {
    mvc.perform(post("/prodotti/GUA-L/riassortimento").with(csrf()).param("quantita", "0"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("La quantità da riassortire va da 1 a 1000")));
  }

  @Test
  @WithMockUser(roles = "MAGAZZINIERE")
  void riassortimentoValidoPortaAiMovimenti() throws Exception {
    mvc.perform(post("/prodotti/MET-5/riassortimento").with(csrf())
        .param("quantita", "10").param("nota", "Arrivo"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/movimenti"));
    mvc.perform(get("/movimenti")).andExpect(status().isOk())
        .andExpect(content().string(containsString("Arrivo")));
  }

  @Test
  void utenteNonPuoCensireAggiungereOSmaltireProdotti() throws Exception {
    mvc.perform(get("/prodotti/nuovo")).andExpect(status().isForbidden());
    mvc.perform(post("/prodotti/nuovo").with(csrf())
        .param("codice", "NON-AUTORIZZATO").param("nome", "Prodotto")
        .param("prezzo", "1.00").param("scortaMinima", "0"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/prodotti/GUA-L/riassortimento"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/prodotti/GUA-L/riassortimento").with(csrf())
        .param("quantita", "1")).andExpect(status().isForbidden());
    mvc.perform(get("/prodotti/GUA-L/smaltimento"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/prodotti/GUA-L/smaltimento").with(csrf())
        .param("quantita", "1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "MAGAZZINIERE")
  void magazziniereCensisceProdottiERegistraSmaltimenti() throws Exception {
    mvc.perform(get("/prodotti/nuovo")).andExpect(status().isOk())
        .andExpect(content().string(containsString("Censimento nuovo prodotto")));

    mvc.perform(post("/prodotti/nuovo").with(csrf())
        .param("codice", "WEB-NEW").param("nome", "Prodotto web")
        .param("prezzo", "2.50").param("scortaMinima", "2"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/prodotti"));
    mvc.perform(get("/prodotti")).andExpect(status().isOk())
        .andExpect(content().string(containsString("WEB-NEW")))
        .andExpect(content().string(containsString("Censisci prodotto")))
        .andExpect(content().string(containsString("Aggiungi scorte")));

    mvc.perform(post("/prodotti/WEB-NEW/riassortimento").with(csrf())
        .param("quantita", "5").param("nota", "Carico iniziale"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/movimenti"));
    mvc.perform(post("/prodotti/WEB-NEW/smaltimento").with(csrf())
        .param("quantita", "2").param("nota", "Cessazione"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/movimenti"));

    mvc.perform(get("/movimenti")).andExpect(status().isOk())
        .andExpect(content().string(containsString("SMALTIMENTO")))
        .andExpect(content().string(containsString("Cessazione")));
    mvc.perform(get("/ordini")).andExpect(status().isForbidden());
  }
}
