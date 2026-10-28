package com.epicode.magazzino.web;

import com.epicode.magazzino.model.RisorsaNonTrovataException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Un ordine o un prodotto che non esiste diventa una pagina 404 leggibile. */
@ControllerAdvice
public class GestoreErrori {

  @ModelAttribute("amministratore")
  public boolean amministratore(Authentication autenticazione) {
    return autenticazione != null
        && autenticazione.getAuthorities().stream().anyMatch(
            autorita -> autorita.getAuthority().equals("ROLE_AMMINISTRATORE"));
  }

  @ModelAttribute("magazziniere")
  public boolean magazziniere(Authentication autenticazione) {
    return autenticazione != null
        && autenticazione.getAuthorities().stream().anyMatch(
            autorita -> autorita.getAuthority().equals("ROLE_MAGAZZINIERE"));
  }

  @ExceptionHandler(RisorsaNonTrovataException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String nonTrovato(RisorsaNonTrovataException e, Model model) {
    model.addAttribute("errore", e.getMessage());
    return "errore";
  }
}
