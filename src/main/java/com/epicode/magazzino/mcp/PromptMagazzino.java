package com.epicode.magazzino.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * Un prompt MCP: lo sceglie la persona (in VS Code con
 * /magazzino.piano-riordino), non il modello. Prepara una richiesta che usa
 * solo tool di lettura.
 */
@Component
public class PromptMagazzino {

  @McpPrompt(name = "piano-riordino", title = "Piano di riordino",
      description = "Proposta di riordino dei prodotti sotto scorta, "
          + "senza modificare il magazzino")
  public GetPromptResult pianoRiordino(@McpArg(name = "copertura",
      description = "Quante volte la scorta minima raggiungere, da 1 a 5 "
          + "(se manca: 2)") Integer copertura) {
    int volte = copertura == null ? 2 : copertura;
    if (volte < 1 || volte > 5) {
      throw new IllegalArgumentException("La copertura va da 1 a 5");
    }
    String testo = """
        Usa il tool prodotti_sotto_scorta. Per ogni prodotto proponi quanti \
        pezzi ordinare per arrivare a %d volte la scorta minima, in una \
        tabella con codice, nome, disponibili, scorta minima e pezzi da \
        ordinare. Non chiamare riassortisci_prodotto: è solo una proposta, \
        la decisione è mia.""".formatted(volte);
    return GetPromptResult
        .builder(List.of(new PromptMessage(Role.USER,
            TextContent.builder(testo).build())))
        .description("Piano di riordino").build();
  }
}
