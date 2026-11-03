package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.RuoloUtente;
import com.epicode.magazzino.model.Utente;

public record UtenteBloccatoMcp(Long id, String username, String nome,
    String cognome, RuoloUtente ruolo, int errori) {

  static UtenteBloccatoMcp da(Utente utente) {
    return new UtenteBloccatoMcp(utente.getId(), utente.getUsername(),
        utente.getNome(), utente.getCognome(),
        RuoloUtente.daValore(utente.getRuolo()), utente.getErrori());
  }
}
