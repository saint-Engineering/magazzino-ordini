package com.epicode.magazzino.mcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

class PromptMagazzinoTest {

  private final PromptMagazzino prompt = new PromptMagazzino();

  @Test
  void coperturaAssenteUsaDueEProponeSenzaModificareIlMagazzino() {
    String testo = testoPrompt(null);

    assertTrue(testo.contains("2 volte la scorta minima"));
    assertTrue(testo.contains("Non chiamare riassortisci_prodotto"));
  }

  @Test
  void coperturaPersonalizzataCompareNelPrompt() {
    assertTrue(testoPrompt(5).contains("5 volte la scorta minima"));
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
}