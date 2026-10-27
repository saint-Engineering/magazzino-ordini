package com.epicode.magazzino.web;

import com.epicode.magazzino.service.UtenteService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Gestisce il cambio password dell'account autenticato corrente. */
@Controller
public class ProfiloController {

  private final UtenteService utenti;

  public ProfiloController(UtenteService utenti) {
    this.utenti = utenti;
  }

  @GetMapping("/profilo/password")
  public String paginaPassword(Model model) {
    model.addAttribute("form", new CambioPasswordForm());
    return "cambio-password";
  }

  @PostMapping("/profilo/password")
  public String cambiaPassword(@ModelAttribute("form") CambioPasswordForm form,
      Authentication autenticazione, Model model, RedirectAttributes redirect) {
    try {
      utenti.cambiaPassword(autenticazione.getName(), form.getPasswordAttuale(),
          form.getNuovaPassword(), form.getConfermaPassword());
      redirect.addFlashAttribute("messaggio", "Password aggiornata");
      return "redirect:/profilo/password";
    } catch (IllegalArgumentException e) {
      model.addAttribute("errore", e.getMessage());
      return "cambio-password";
    }
  }
}
