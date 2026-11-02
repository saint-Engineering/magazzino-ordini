package com.epicode.magazzino.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromptMagazzinoTest {

  private final PromptMagazzino prompt = new PromptMagazzino();

  @Test
  void coperturaAssenteUsaDueEProponeSenzaModificareIlMagazzino() {
    String testo = testoPrompt(null);

    assertTrue(testo.contains("2 volte la scorta minima"));
    assertTrue(testo.contains("Non chiamare riassortisci_prodotto"));
  }

  @Test
  void senzaCoperturaProponeDueVolteLaScortaMinima() {
    McpSchema.GetPromptResult risultato = prompt.pianoRiordino(null);

    assertEquals("Piano di riordino", risultato.description());
    assertEquals(1, risultato.messages().size());
    assertEquals(Role.USER, risultato.messages().get(0).role());
    TextContent contenuto = assertInstanceOf(TextContent.class,
        risultato.messages().get(0).content());
    assertEquals(
        "Usa il tool prodotti_sotto_scorta. Per ogni prodotto proponi quanti "
            + "pezzi ordinare per arrivare a 2 volte la scorta minima, in una "
            + "tabella con codice, nome, disponibili, scorta minima e pezzi da "
            + "ordinare. Non chiamare riassortisci_prodotto: \u00e8 solo una proposta, "
            + "la decisione \u00e8 mia.",
        contenuto.text());
  }

  @Test
  void coperturaPersonalizzataCompareNelPrompt() {
    assertTrue(testoPrompt(5).contains("5 volte la scorta minima"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 5})
  void daUnaACinqueVolteUsaLaCoperturaRichiesta(int copertura) {
    GetPromptResult risultato = prompt.pianoRiordino(copertura);

    assertEquals(1, risultato.messages().size());
    TextContent contenuto = assertInstanceOf(TextContent.class,
        risultato.messages().get(0).content());
    assertTrue(contenuto.text().contains(copertura + " volte"));
    assertTrue(contenuto.text().contains("prodotti_sotto_scorta"));
    assertTrue(contenuto.text()
        .contains("Non chiamare riassortisci_prodotto"));
  }

  @Test
  void coperturaFuoriIntervalloVieneRifiutata() {
    assertThrows(IllegalArgumentException.class,
        () -> prompt.pianoRiordino(0));
    assertThrows(IllegalArgumentException.class,
        () -> prompt.pianoRiordino(6));
  }

  private String testoPrompt(Integer copertura) {
    return ((TextContent) prompt.pianoRiordino(copertura).messages().get(0)
        .content()).text();
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, 0, 6, Integer.MAX_VALUE})
  void fuoriDaUnoACinqueRifiutaLaCopertura(int copertura) {
    IllegalArgumentException errore = assertThrows(
        IllegalArgumentException.class,
        () -> prompt.pianoRiordino(copertura));

    assertEquals("La copertura va da 1 a 5", errore.getMessage());
  }
}
