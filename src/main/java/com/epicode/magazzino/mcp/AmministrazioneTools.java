package com.epicode.magazzino.mcp;

import com.epicode.magazzino.service.UtenteService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class AmministrazioneTools {

  private final UtenteService utenti;

  public AmministrazioneTools(UtenteService utenti) {
    this.utenti = utenti;
  }

  @McpTool(name = "utenti_bloccati", description = """
      Elenca tutti gli account bloccati per errori di login, di qualsiasi \
      ruolo, ordinati per username. Restituisce id, username, nome, cognome, \
      ruolo e numero di errori, senza credenziali. Sola lettura: non sblocca \
      gli account e non modifica i contatori.""", generateOutputSchema = true, annotations = @McpAnnotations(title = "Utenti bloccati", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
  @PreAuthorize("hasRole('AMMINISTRATORE')")
  public ElencoUtentiBloccati utentiBloccati() {
    return ElencoUtentiBloccati.di(utenti.elencoBloccati());
  }
}
