package com.epicode.magazzino.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.model.RichiestaNonValidaException;
import com.epicode.magazzino.repository.ProdottoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Le due letture nuove del catalogo, usate dai tool MCP (M7 G2). */
@SpringBootTest
@Transactional
class RicercaScorteTest {

  @Autowired
  private CatalogoService catalogo;

  @Autowired
  private ProdottoRepository prodotti;

  @BeforeEach
  void prodottiDiTest() {
    // scoperti di 7, 0 (giacenza uguale alla scorta) e coperto
    prodotti.save(new Prodotto("T-SC-A", "Rondelle di prova",
        new BigDecimal("1.00"), 3, 10));
    prodotti.save(new Prodotto("T-SC-B", "Bulloni di prova",
        new BigDecimal("1.00"), 5, 5));
    prodotti.save(new Prodotto("T-SC-C", "Dadi di prova",
        new BigDecimal("1.00"), 6, 5));
  }

  private List<String> codici(List<Prodotto> elenco) {
    return elenco.stream().map(Prodotto::getCodice)
        .filter(c -> c.startsWith("T-SC-")).toList();
  }

  @Test
  void laRicercaGuardaCodiceENomeSenzaMaiuscole() {
    assertEquals(List.of("T-SC-B", "T-SC-C", "T-SC-A"),
        codici(catalogo.cerca("DI PROVA")));
    assertEquals(List.of("T-SC-A"), codici(catalogo.cerca("t-sc-a")));
  }

  @Test
  void unaRicercaTroppoCortaVieneRifiutata() {
    assertThrows(RichiestaNonValidaException.class, () -> catalogo.cerca(" x "));
    assertThrows(RichiestaNonValidaException.class, () -> catalogo.cerca(null));
  }

  @Test
  void sottoScortaDalPiuScopertoEConLaGiacenzaUgualeAllaScorta() {
    List<String> daRiassortire = codici(catalogo.daRiassortire());

    assertEquals(List.of("T-SC-A", "T-SC-B"), daRiassortire);
    assertTrue(catalogo.daRiassortire().stream()
        .allMatch(Prodotto::daRiassortire));
  }
}
