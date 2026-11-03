package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.MovimentoMagazzino;
import com.epicode.magazzino.model.TipoMovimento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record MovimentoMcp(LocalDateTime creatoIl, String codice, String nome,
    TipoMovimento tipo, int quantita, @Schema(nullable = true) String nota,
    @Schema(nullable = true) Long ordineId) {

  static MovimentoMcp da(MovimentoMagazzino movimento) {
    return new MovimentoMcp(movimento.getCreatoIl(),
        movimento.getProdotto().getCodice(), movimento.getProdotto().getNome(),
        movimento.getTipo(), movimento.getQuantita(), movimento.getNota(),
        movimento.getOrdineId());
  }
}
