package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Prodotto;

public record EsitoSmaltimento(String codice, String nome, int smaltiti,
    int disponibili, boolean daRiassortire) {

  static EsitoSmaltimento di(Prodotto prodotto, int smaltiti) {
    return new EsitoSmaltimento(prodotto.getCodice(), prodotto.getNome(),
        smaltiti, prodotto.getDisponibili(), prodotto.daRiassortire());
  }
}
