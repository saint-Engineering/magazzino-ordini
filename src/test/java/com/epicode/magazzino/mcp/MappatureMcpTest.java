package com.epicode.magazzino.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.model.Prodotto;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappatureMcpTest {

  @Test
  void prodottoMcpEsponeIValoriDelProdotto() {
    Prodotto prodotto = new Prodotto("T-MCP-1", "Cacciavite PH2",
        new BigDecimal("4.10"), 18, 20);

    assertEquals(new ProdottoMcp("T-MCP-1", "Cacciavite PH2",
        new BigDecimal("4.10"), 18, 20, true), ProdottoMcp.da(prodotto));
  }

  @Test
  void elencoProdottiLimitaElementiMaMantieneIlTotale() {
    List<Prodotto> prodotti = List.of(
        new Prodotto("T-MCP-1", "Prodotto uno", new BigDecimal("1.00"), 1, 2),
        new Prodotto("T-MCP-2", "Prodotto due", new BigDecimal("2.00"), 2, 3),
        new Prodotto("T-MCP-3", "Prodotto tre", new BigDecimal("3.00"), 3, 4));

    ElencoProdotti elenco = ElencoProdotti.di(prodotti, 2);

    assertEquals(2, elenco.prodotti().size());
    assertEquals(3, elenco.trovati());
    assertEquals(List.of("T-MCP-1", "T-MCP-2"),
        elenco.prodotti().stream().map(ProdottoMcp::codice).toList());
  }

  @Test
  void esitoRiassortimentoRiportaGiacenzaAggiornata() {
    Prodotto prodotto = new Prodotto("T-MCP-1", "Cacciavite PH2",
        new BigDecimal("4.10"), 18, 20);
    prodotto.riassortisci(22);

    EsitoRiassortimento esito = EsitoRiassortimento.di(prodotto, 22);

    assertEquals(
        new EsitoRiassortimento("T-MCP-1", "Cacciavite PH2", 22, 40, false),
        esito);
    assertFalse(esito.ancoraDaRiassortire());
  }

  @Test
  void ordineMcpConverteRigheEImporti() throws ReflectiveOperationException {
    LocalDateTime creazione = LocalDateTime.of(2026, 2, 1, 10, 15);
    Ordine ordine = new Ordine("Cliente MCP", creazione);
    impostaId(ordine, 42L);
    ordine.aggiungiRiga(new Prodotto("T-MCP-1", "Cacciavite PH2",
        new BigDecimal("12.50"), 5, 1), 1);

    OrdineMcp risultato = OrdineMcp.da(ordine);

    assertEquals(42L, risultato.numero());
    assertEquals("Cliente MCP", risultato.cliente());
    assertEquals("CONFERMATO", risultato.stato());
    assertEquals(creazione.toString(), risultato.creatoIl());
    assertEquals(
        List.of(new OrdineMcp.Riga("T-MCP-1", "Cacciavite PH2", 1,
            new BigDecimal("12.50"), 0, new BigDecimal("12.50"))),
        risultato.righe());
    assertEquals(new BigDecimal("12.50"), risultato.totaleMerce());
    assertEquals(new BigDecimal("9.90"), risultato.speseSpedizione());
    assertEquals(new BigDecimal("22.40"), risultato.totaleDaPagare());
  }

  private void impostaId(Ordine ordine, long id)
      throws ReflectiveOperationException {
    Field campoId = Ordine.class.getDeclaredField("id");
    campoId.setAccessible(true);
    campoId.set(ordine, id);
  }
}