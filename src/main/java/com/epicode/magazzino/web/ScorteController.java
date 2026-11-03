package com.epicode.magazzino.web;

import com.epicode.magazzino.service.CatalogoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * R1-R5 (M7 G3): la pagina dei prodotti da riassortire. Usa lo stesso caso
 * d'uso del tool MCP prodotti_sotto_scorta, così pagina e agente vedono gli
 * stessi dati.
 */
@Controller
public class ScorteController {

  private final CatalogoService catalogo;

  public ScorteController(CatalogoService catalogo) {
    this.catalogo = catalogo;
  }

  @GetMapping("/scorte")
  public String scorte(Model model) {
    model.addAttribute("prodotti", catalogo.daRiassortire());
    return "scorte";
  }
}
