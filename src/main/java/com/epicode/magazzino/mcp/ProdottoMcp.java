package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Prodotto;
import java.math.BigDecimal;

/**
 * Un prodotto come lo vede l'agente: solo i campi che gli servono, mai
 * l'entità JPA (che porterebbe con sé id interni e relazioni).
 */
public record ProdottoMcp(String codice, String nome, BigDecimal prezzo,
    int disponibili, int scortaMinima, boolean daRiassortire) {

  static ProdottoMcp da(Prodotto prodotto) {
    return new ProdottoMcp(prodotto.getCodice(), prodotto.getNome(),
        prodotto.getPrezzo(), prodotto.getDisponibili(),
        prodotto.getScortaMinima(), prodotto.daRiassortire());
  }
}
