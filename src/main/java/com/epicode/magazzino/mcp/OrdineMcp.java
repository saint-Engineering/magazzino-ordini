package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.model.RigaOrdine;
import java.math.BigDecimal;
import java.util.List;

/** Un ordine con le sue righe, come lo vede l'agente. */
public record OrdineMcp(long numero, String cliente, String stato,
    String creatoIl, List<Riga> righe, BigDecimal totaleMerce,
    BigDecimal speseSpedizione, BigDecimal totaleDaPagare) {

  /** Una riga dell'ordine, con lo sconto per quantità già applicato. */
  public record Riga(String codiceProdotto, String nome, int quantita,
      BigDecimal prezzoUnitario, int scontoPercento, BigDecimal subtotale) {

    static Riga da(RigaOrdine riga) {
      return new Riga(riga.getProdotto().getCodice(),
          riga.getProdotto().getNome(), riga.getQuantita(),
          riga.getPrezzoUnitario(), riga.percentualeSconto(),
          riga.subtotale());
    }
  }

  static OrdineMcp da(Ordine ordine) {
    return new OrdineMcp(ordine.getId(), ordine.getCliente(),
        ordine.getStato().name(), ordine.getCreatoIl().toString(),
        ordine.getRighe().stream().map(Riga::da).toList(), ordine.totale(),
        ordine.speseSpedizione(), ordine.totaleDaPagare());
  }
}
