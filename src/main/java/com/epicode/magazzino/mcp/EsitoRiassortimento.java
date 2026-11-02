package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Prodotto;

/** Cosa è cambiato dopo un riassortimento: il modello lo può riferire. */
public record EsitoRiassortimento(String codice, String nome, int aggiunti,
    int disponibili, boolean ancoraDaRiassortire) {

  static EsitoRiassortimento di(Prodotto prodotto, int aggiunti) {
    return new EsitoRiassortimento(prodotto.getCodice(), prodotto.getNome(),
        aggiunti, prodotto.getDisponibili(), prodotto.daRiassortire());
  }
}
