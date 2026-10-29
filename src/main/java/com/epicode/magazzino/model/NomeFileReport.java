package com.epicode.magazzino.model;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Il nome di un file di report richiesto dall'utente. Si accettano solo nomi
 * semplici ".csv": niente cartelle, niente "..", niente percorsi assoluti.
 */
public record NomeFileReport(String valore) {

  private static final Pattern AMMESSO = Pattern
      .compile("[a-z0-9-]{1,60}\\.csv");

  public NomeFileReport {
    if (valore == null || !AMMESSO.matcher(valore).matches()) {
      throw new RichiestaNonValidaException("Nome di report non valido");
    }
  }

  /**
   * Il percorso dentro la cartella dei report; il controllo finale è una
   * seconda difesa.
   */
  public Path dentro(Path cartella) {
    Path base = cartella.toAbsolutePath().normalize();
    Path file = base.resolve(valore).normalize();
    if (!file.startsWith(base)) {
      throw new RichiestaNonValidaException("Nome di report non valido");
    }
    return file;
  }
}
