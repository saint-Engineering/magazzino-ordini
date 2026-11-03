package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.repository.ProdottoRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** R1-R4 della pagina /scorte (M7 G3), con il catalogo di DatiIniziali. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "UTENTE")
class ScortePaginaTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ProdottoRepository prodotti;

  @Test
  void elencaISottoScortaDalPiuScopertoConIMancanti() throws Exception {
    String pagina = mvc.perform(get("/scorte")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    // R1: NAS-50 (mancano 10), TAS-8 (5), CAC-PH2 (2); GUA-L è coperto
    int nastro = pagina.indexOf("NAS-50");
    int tasselli = pagina.indexOf("TAS-8");
    int cacciavite = pagina.indexOf("CAC-PH2");
    assertTrue(nastro > 0 && nastro < tasselli && tasselli < cacciavite);
    assertEquals(-1, pagina.indexOf("GUA-L"));
    // R3: ogni riga offre il riassortimento del prodotto corrispondente
    assertTrue(pagina.contains("/prodotti/NAS-50/riassortimento"));
    assertTrue(pagina.contains("/prodotti/TAS-8/riassortimento"));
    assertTrue(pagina.contains("/prodotti/CAC-PH2/riassortimento"));
  }

  @Test
  void mostraMancantiEsattiEIncludeLaGiacenzaUgualeAllaScorta()
      throws Exception {
    prodotti.save(new Prodotto("SCORTE-TEST-SOTTO", "Sotto soglia",
        BigDecimal.ONE, 3, 10));
    prodotti.save(new Prodotto("SCORTE-TEST-PARI", "Alla soglia",
        BigDecimal.ONE, 10, 10));
    prodotti.save(new Prodotto("SCORTE-TEST-SOPRA", "Sopra soglia",
        BigDecimal.ONE, 12, 10));

    String pagina = mvc.perform(get("/scorte")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    String rigaSotto = rigaProdotto(pagina, "SCORTE-TEST-SOTTO")
        .replaceAll("\\s+", " ");
    String rigaPari = rigaProdotto(pagina, "SCORTE-TEST-PARI")
        .replaceAll("\\s+", " ");
    assertTrue(
        rigaSotto.contains("<td class=\"num\">3</td> <td class=\"num\">10</td> "
            + "<td class=\"num\">7</td>"));
    assertTrue(
        rigaPari.contains("<td class=\"num\">10</td> <td class=\"num\">10</td> "
            + "<td class=\"num\">0</td>"));
    assertTrue(pagina.indexOf("SCORTE-TEST-SOTTO") < pagina
        .indexOf("SCORTE-TEST-PARI"));
    assertEquals(-1, pagina.indexOf("SCORTE-TEST-SOPRA"));
  }

  @Test
  void catalogoSenzaProdottiMostraLoStatoVuotoSenzaTabella() throws Exception {
    prodotti.deleteAllInBatch();

    String pagina = mvc.perform(get("/scorte")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertTrue(pagina
        .contains("<p class=\"avviso ok\">Nessun prodotto da riassortire</p>"));
    assertEquals(-1, pagina.indexOf("<table"));
  }

  private String rigaProdotto(String pagina, String codice) {
    int indiceCodice = pagina.indexOf("<code>" + codice + "</code>");
    assertTrue(indiceCodice >= 0, "Prodotto non presente: " + codice);
    int inizioRiga = pagina.lastIndexOf("<tr", indiceCodice);
    int fineRiga = pagina.indexOf("</tr>", indiceCodice);
    return pagina.substring(inizioRiga, fineRiga + "</tr>".length());
  }

  @Test
  void ilMenuPortaAlleScorte() throws Exception {
    mvc.perform(get("/prodotti")).andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/scorte\"")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/scorte", "/prodotti"})
  @WithAnonymousUser
  void visitatoreDeveAutenticarsi(String pagina) throws Exception {
    mvc.perform(get(pagina)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MAGAZZINIERE", "AMMINISTRATORE"})
  void altriRuoliNonAccedonoAlleScorte(String ruolo) throws Exception {
    mvc.perform(get("/scorte").with(user("operatore").roles(ruolo)))
        .andExpect(status().isForbidden());
  }

  @Test
  void iPezziMancantiNonSonoMaiNegativi() {
    // R2: 0 se la giacenza è uguale o sopra la scorta minima
    assertEquals(7,
        new Prodotto("T-1", "A", BigDecimal.ONE, 3, 10).pezziMancanti());
    assertEquals(0,
        new Prodotto("T-2", "B", BigDecimal.ONE, 10, 10).pezziMancanti());
    assertEquals(0,
        new Prodotto("T-3", "C", BigDecimal.ONE, 12, 10).pezziMancanti());
  }
}
