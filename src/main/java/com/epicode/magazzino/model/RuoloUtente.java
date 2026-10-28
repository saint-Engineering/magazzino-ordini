package com.epicode.magazzino.model;

/** I ruoli persistiti nella tabella UTENTI. */
public enum RuoloUtente {
  AMMINISTRATORE(0), UTENTE(1), MAGAZZINIERE(2);

  private final int valore;

  RuoloUtente(int valore) {
    this.valore = valore;
  }

  public int getValore() {
    return valore;
  }

  public static RuoloUtente daValore(int valore) {
    for (RuoloUtente ruolo : values()) {
      if (ruolo.valore == valore) {
        return ruolo;
      }
    }
    throw new IllegalArgumentException("Ruolo non valido");
  }
}
