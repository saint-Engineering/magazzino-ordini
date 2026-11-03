package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epicode.magazzino.model.Prodotto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** R1-R4 della pagina /scorte (M7 G3), con il catalogo di DatiIniziali. */
@SpringBootTest
@AutoConfigureMockMvc
class ScortePaginaTest {

  @Autowired
  private MockMvc mvc;

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
    // R3: il link al riassortimento di ogni riga
    assertTrue(pagina.contains("/prodotti/TAS-8/riassortimento"));
  }

  @Test
  void ilMenuPortaAlleScorte() throws Exception {
    mvc.perform(get("/prodotti"))
        .andExpect(content().string(containsString("href=\"/scorte\"")));
  }

  @Test
  void iPezziMancantiNonSonoMaiNegativi() {
    // R2: 0 se la giacenza è uguale o sopra la scorta minima
    assertEquals(7, new Prodotto("T-1", "A", BigDecimal.ONE, 3, 10)
        .pezziMancanti());
    assertEquals(0, new Prodotto("T-2", "B", BigDecimal.ONE, 10, 10)
        .pezziMancanti());
    assertEquals(0, new Prodotto("T-3", "C", BigDecimal.ONE, 12, 10)
        .pezziMancanti());
  }
}
