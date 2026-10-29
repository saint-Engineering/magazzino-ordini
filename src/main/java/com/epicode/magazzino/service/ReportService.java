package com.epicode.magazzino.service;

import com.epicode.magazzino.model.IntestazioneReport;
import com.epicode.magazzino.model.NomeFileReport;
import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.model.RisorsaNonTrovataException;
import com.epicode.magazzino.repository.OrdineRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il modulo report rifatto: query parametrizzate, file solo dalla cartella dei
 * report.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

  private static final DateTimeFormatter DATA = DateTimeFormatter
      .ofPattern("dd/MM/yyyy");

  private final OrdineRepository ordini;
  private final Path cartella;
  private final Clock clock;

  public ReportService(OrdineRepository ordini,
      @Value("${report.cartella:report}") String cartella, Clock clock) {
    this.ordini = ordini;
    this.cartella = Path.of(cartella);
    this.clock = clock;
  }

  public List<Ordine> ordiniDi(String cliente) {
    return ordini.findByClienteOrderByCreatoIlDesc(cliente);
  }

  public byte[] file(NomeFileReport nome) {
    Path file = nome.dentro(cartella);
    if (!Files.isRegularFile(file)) {
      throw new RisorsaNonTrovataException(
          "Report " + nome.valore() + " non trovato");
    }
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Un solo segnaposto, {data}: nessun interprete di espressioni sull'input
   * dell'utente.
   */
  public String intestazione(String modello) {
    return modello.replace("{data}", LocalDate.now(clock).format(DATA));
  }

  public String intestazioneStorica(String modello) {
    return IntestazioneReport.sostituisciData(modello, LocalDate.now(clock));
  }
}
