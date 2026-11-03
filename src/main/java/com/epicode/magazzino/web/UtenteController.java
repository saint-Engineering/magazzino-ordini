package com.epicode.magazzino.web;

import com.epicode.magazzino.model.Utente;
import com.epicode.magazzino.service.UtenteConChiaveMcp;
import com.epicode.magazzino.service.UtenteService;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  @GetMapping("/bloccati")
  public String bloccati(Model model) {
    model.addAttribute("utenti", utenti.elencoBloccati());
    return "utenti-bloccati";
  }

  @GetMapping("/nuovo")
  public String nuovo(Model model) {
    model.addAttribute("form", new UtenteForm());
    model.addAttribute("modifica", false);
    return "utente-form";
  }

  @PostMapping("/nuovo")
  public Object crea(@ModelAttribute("form") UtenteForm form, Model model) {
    try {
      UtenteConChiaveMcp creato = utenti.crea(form.getUsername(),
          form.getPassword(), form.getNome(), form.getCognome(),
          form.getRuolo());
      return fileChiave(creato.utente().getId(), creato.chiaveMcp());
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
    sbloccaUtente(id, redirect);
    return "redirect:/utenti";
  }

  @PostMapping("/bloccati/{id}/sblocca")
  public String sbloccaDallaListaBloccati(@PathVariable Long id,
      RedirectAttributes redirect) {
    sbloccaUtente(id, redirect);
    return "redirect:/utenti/bloccati";
  }

  private void sbloccaUtente(Long id, RedirectAttributes redirect) {
    try {
      utenti.sblocca(id);
      redirect.addFlashAttribute("messaggio", "Utente sbloccato");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("errore", e.getMessage());
    }
  }

  @PostMapping("/{id}/chiave-mcp")
  public ResponseEntity<byte[]> generaChiaveMcp(@PathVariable Long id) {
    return fileChiave(id, utenti.generaChiaveMcp(id));
  }

  @PostMapping("/{id}/chiave-mcp/revoca")
  public String revocaChiaveMcp(@PathVariable Long id,
      RedirectAttributes redirect) {
    try {
      utenti.revocaChiaveMcp(id);
      redirect.addFlashAttribute("messaggio", "Chiave MCP revocata");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("errore", e.getMessage());
    }
    return "redirect:/utenti";
  }

  private ResponseEntity<byte[]> fileChiave(Long id, String chiave) {
    byte[] contenuto = ("Authorization: Bearer " + chiave
        + System.lineSeparator()
        + "Conserva questo file in modo sicuro: la chiave non sarà mostrata "
        + "di nuovo.").getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA,
            "no-cache")
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("chiave-mcp-utente-" + id + ".txt",
                    StandardCharsets.UTF_8)
                .build().toString())
        .body(contenuto);
  }
}
