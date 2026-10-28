package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.repository.OrdineRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "UTENTE")
class ReportControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private OrdineRepository ordini;

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
}
