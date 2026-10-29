package com.epicode.magazzino.web;

import com.epicode.magazzino.model.NomeFileReport;
import com.epicode.magazzino.model.RichiestaNonValidaException;
import com.epicode.magazzino.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

@Controller
@RequestMapping("/report")
public class ReportController {

  private final ReportService report;

  public ReportController(ReportService report) {
    this.report = report;
  }

  @GetMapping("/ordini")
  public String ordini(@RequestParam String cliente, Model model) {
    model.addAttribute("righe", report.ordiniDi(cliente));
    model.addAttribute("titolo", "Ordini di " + cliente);
    return "report";
  }

  @GetMapping("/file")
  public ResponseEntity<byte[]> file(@RequestParam String nome) {
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
        .body(report.file(new NomeFileReport(nome)));
  }

  @GetMapping("/intestazione")
  public ResponseEntity<String> intestazione(@RequestParam String modello) {
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
        .body(HtmlUtils.htmlEscapeHex(report.intestazioneStorica(modello)));
  }

  @GetMapping("/intestazione-testo")
  public ResponseEntity<String> intestazioneTesto(
      @RequestParam String modello) {
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
        .body(report.intestazione(modello));
  }

  @ExceptionHandler(RichiestaNonValidaException.class)
  public ResponseEntity<String> richiestaNonValida(
      RichiestaNonValidaException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
  }
}
