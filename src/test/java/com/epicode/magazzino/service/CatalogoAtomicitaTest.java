package com.epicode.magazzino.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.epicode.magazzino.model.MovimentoMagazzino;
import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.model.Riassortimento;
import com.epicode.magazzino.model.Smaltimento;
import com.epicode.magazzino.repository.MovimentoRepository;
import com.epicode.magazzino.repository.ProdottoRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class CatalogoAtomicitaTest {

  @Autowired
  private CatalogoService catalogo;

  @Autowired
  private ProdottoRepository prodotti;

  @MockitoSpyBean
  private MovimentoRepository movimenti;

  private Long prodottoId;

  @BeforeEach
  void preparaProdotto() {
    prodottoId = prodotti.save(new Prodotto("T-ATOM", "Prodotto atomico",
        new BigDecimal("1.00"), 10, 2)).getId();
  }

  @AfterEach
  void eliminaProdotto() {
    prodotti.deleteById(prodottoId);
  }

  @Test
  void erroreNelMovimentoAnnullaAncheIlCarico() {
    doThrow(
        new DataIntegrityViolationException("Errore di persistenza simulato"))
        .when(movimenti).save(any(MovimentoMagazzino.class));

    assertThrows(DataIntegrityViolationException.class, () -> catalogo
        .riassortisci("T-ATOM", new Riassortimento(4, "Consegna")));

    assertEquals(10, catalogo.trova("T-ATOM").getDisponibili());
    assertEquals(0, catalogo.movimenti().stream()
        .filter(m -> m.getProdotto().getId().equals(prodottoId)).count());
  }

  @Test
  void erroreNelMovimentoAnnullaAncheLoSmaltimento() {
    doThrow(
        new DataIntegrityViolationException("Errore di persistenza simulato"))
        .when(movimenti).save(any(MovimentoMagazzino.class));

    assertThrows(DataIntegrityViolationException.class,
        () -> catalogo.smaltisci("T-ATOM", new Smaltimento(4, "Cessazione")));

    assertEquals(10, catalogo.trova("T-ATOM").getDisponibili());
    assertEquals(0, catalogo.movimenti().stream()
        .filter(m -> m.getProdotto().getId().equals(prodottoId)).count());
  }
}
