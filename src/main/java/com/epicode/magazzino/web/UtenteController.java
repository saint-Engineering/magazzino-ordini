package com.epicode.magazzino.web;

import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.service.UtenteService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Gestisce le pagine di amministrazione degli account. */
@Controller
@RequestMapping("/utenti")
public class UtenteController {

  private final UtenteService utenti;

  public UtenteController(UtenteService utenti) {
    this.utenti = utenti;
  }

  @GetMapping
  public String elenco(Model model) {
    model.addAttribute("utenti", utenti.elenco());
    return "utenti";
  }

  @GetMapping("/nuovo")
  public String nuovo(Model model) {
    model.addAttribute("form", new UtenteForm());
    model.addAttribute("modifica", false);
    return "utente-form";
  }

  @PostMapping("/nuovo")
  public String crea(@ModelAttribute("form") UtenteForm form, Model model) {
    try {
      utenti.crea(form.getUsername(), form.getPassword(), form.getNome(),
          form.getCognome(), form.getRuolo());
      return "redirect:/utenti";
    } catch (IllegalArgumentException | IllegalStateException
        | DataIntegrityViolationException e) {
      model.addAttribute("errore", e.getMessage());
      model.addAttribute("modifica", false);
      return "utente-form";
    }
  }

  @GetMapping("/{id}/modifica")
  public String modifica(@PathVariable Long id, Model model) {
    Utente utente = utenti.trova(id);
    UtenteForm form = new UtenteForm();
    form.setUsername(utente.getUsername());
    form.setNome(utente.getNome());
    form.setCognome(utente.getCognome());
    form.setRuolo(utente.getRuolo());
    model.addAttribute("form", form);
    model.addAttribute("utente", utente);
    model.addAttribute("modifica", true);
    return "utente-form";
  }

  @PostMapping("/{id}/modifica")
  public String salvaModifica(@PathVariable Long id,
      @ModelAttribute("form") UtenteForm form, Model model) {
    try {
      utenti.modifica(id, form.getUsername(), form.getNome(), form.getCognome(),
          form.getRuolo());
      return "redirect:/utenti";
    } catch (IllegalArgumentException | DataIntegrityViolationException e) {
      model.addAttribute("errore", e.getMessage());
      model.addAttribute("utente", utenti.trova(id));
      model.addAttribute("modifica", true);
      return "utente-form";
    }
  }

  @PostMapping("/{id}/elimina")
  public String elimina(@PathVariable Long id, RedirectAttributes redirect) {
    try {
      utenti.elimina(id);
      redirect.addFlashAttribute("messaggio", "Utente eliminato");
    } catch (IllegalArgumentException | IllegalStateException e) {
      redirect.addFlashAttribute("errore", e.getMessage());
    }
    return "redirect:/utenti";
  }

  @PostMapping("/{id}/sblocca")
  public String sblocca(@PathVariable Long id, RedirectAttributes redirect) {
    try {
      utenti.sblocca(id);
      redirect.addFlashAttribute("messaggio", "Utente sbloccato");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("errore", e.getMessage());
    }
    return "redirect:/utenti";
  }
}
