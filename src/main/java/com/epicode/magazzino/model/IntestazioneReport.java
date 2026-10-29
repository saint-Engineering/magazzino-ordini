package com.epicode.magazzino.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IntestazioneReport {

  private static final Pattern SEGNAPOSTO_DATA = Pattern
      .compile("\\$\\{date:([^{}]+)}");

  private IntestazioneReport() {
  }

  public static String sostituisciData(String modello, LocalDate data) {
    Matcher matcher = SEGNAPOSTO_DATA.matcher(modello);
    StringBuilder risultato = new StringBuilder();
    while (matcher.find()) {
      String valore;
      try {
        valore = data.format(DateTimeFormatter.ofPattern(matcher.group(1)));
      } catch (IllegalArgumentException e) {
        throw new RichiestaNonValidaException("Formato data non valido.");
      }
      matcher.appendReplacement(risultato, Matcher.quoteReplacement(valore));
    }
    matcher.appendTail(risultato);
    return risultato.toString();
  }
}
