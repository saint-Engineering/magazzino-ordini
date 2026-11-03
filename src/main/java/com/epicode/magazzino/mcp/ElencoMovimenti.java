package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.MovimentoMagazzino;
import java.util.List;

public record ElencoMovimenti(List<MovimentoMcp> movimenti) {

  static ElencoMovimenti di(List<MovimentoMagazzino> movimenti) {
    return new ElencoMovimenti(
        movimenti.stream().map(MovimentoMcp::da).toList());
  }
}
