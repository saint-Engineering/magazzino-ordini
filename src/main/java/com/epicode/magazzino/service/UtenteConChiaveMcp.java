package com.epicode.magazzino.service;

import com.epicode.magazzino.model.Utente;

/** Segreto MCP mostrato una sola volta insieme all'utente appena creato. */
public record UtenteConChiaveMcp(Utente utente, String chiaveMcp) {

  @Override
  public String toString() {
    return "UtenteConChiaveMcp[utente=" + utente.getUsername()
        + ", chiaveMcp=[REDACTED]]";
  }
}
