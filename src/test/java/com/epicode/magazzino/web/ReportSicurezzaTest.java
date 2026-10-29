package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epicode.magazzino.service.NuovoOrdine;
import com.epicode.magazzino.service.OrdineService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Gli attacchi che funzionavano sul codice del fornitore (tag m6-g3-fornitore).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "UTENTE")
class ReportSicurezzaTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private OrdineService ordini;

  @Test
  void sqlInjectionNonRestituisceGliOrdiniDegliAltri() throws Exception {
    ordini.crea(new NuovoOrdine("Cliente Riservato",
        List.of(new NuovoOrdine.Riga("GUA-L", 1))));
    mvc.perform(get("/report/ordini").param("cliente", "' OR '1'='1"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Cliente Riservato"))));
  }

  @Test
  void ilNomeDelClienteNonDiventaHtml() throws Exception {
    mvc.perform(
        get("/report/ordini").param("cliente", "<script>alert(1)</script>"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(not(containsString("<script>alert(1)</script>"))))
        .andExpect(content().string(containsString("&lt;script&gt;")));
  }

  @Test
  void pathTraversalRifiutato() throws Exception {
    mvc.perform(get("/report/file").param("nome", "../pom.xml"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unReportVeroSiScarica() throws Exception {
    mvc.perform(get("/report/file").param("nome", "ordini-ottobre.csv"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Ferramenta Bianchi")));
  }

  @Test
  void lIntestazioneNonEsegueEspressioni() throws Exception {
    mvc.perform(
        get("/report/intestazione").param("modello", "${sys:user.home}"))
        .andExpect(status().isOk())
        .andExpect(content().string("${sys:user.home}"));
  }
}
