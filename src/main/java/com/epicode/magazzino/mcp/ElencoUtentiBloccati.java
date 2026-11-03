package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Utente;
import java.util.List;

public record ElencoUtentiBloccati(List<UtenteBloccatoMcp> utenti, int totale) {

  static ElencoUtentiBloccati di(List<Utente> utenti) {
    List<UtenteBloccatoMcp> bloccati = utenti.stream()
        .map(UtenteBloccatoMcp::da).toList();
    return new ElencoUtentiBloccati(bloccati, bloccati.size());
  }
}
