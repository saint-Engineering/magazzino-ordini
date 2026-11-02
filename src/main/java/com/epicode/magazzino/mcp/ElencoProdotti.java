package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Prodotto;
import java.util.List;

/**
 * Una lista di prodotti per l'agente. È un oggetto e non un array perché il
 * risultato strutturato di un tool MCP deve essere un oggetto JSON; "trovati"
 * dice quanti erano prima del taglio, così il modello sa se ne mancano.
 */
public record ElencoProdotti(List<ProdottoMcp> prodotti, int trovati) {

  static ElencoProdotti di(List<Prodotto> tutti, int massimo) {
    List<ProdottoMcp> primi = tutti.stream().limit(massimo)
        .map(ProdottoMcp::da).toList();
    return new ElencoProdotti(primi, tutti.size());
  }
}
