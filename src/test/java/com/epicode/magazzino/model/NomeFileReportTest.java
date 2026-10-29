package com.epicode.magazzino.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Test d'attacco sul path traversal: ogni nome qui sotto è un tentativo vero.
 */
class NomeFileReportTest {

  @Test
  void unNomeSempliceCsvEAmmesso() {
    Path file = new NomeFileReport("ordini-ottobre.csv")
        .dentro(Path.of("report"));
    assertEquals("ordini-ottobre.csv", file.getFileName().toString());
  }

  @Test
  void risalireDiCartellaNonSiPuo() {
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("../pom.xml"));
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("..%2Fpom.xml"));
  }

  @Test
  void percorsiAssolutiESottocartelleNo() {
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("/etc/passwd"));
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("C:\\Windows\\win.ini"));
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("vecchi/ordini.csv"));
  }

  @Test
  void soloCsv() {
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("ordini.txt"));
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport(null));
  }

  @Test
  void lunghezzaDelNomeBaseVaDaUnoASessanta() {
    assertEquals("a.csv", new NomeFileReport("a.csv").valore());
    String massimo = "a".repeat(60) + ".csv";
    assertEquals(massimo, new NomeFileReport(massimo).valore());
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport(".csv"));
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("a".repeat(61) + ".csv"));
  }

  @Test
  void maiuscoleEUnderscoreNonSonoAmmessi() {
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("Ordini.csv"));
    assertThrows(RichiestaNonValidaException.class,
        () -> new NomeFileReport("ordini_ottobre.csv"));
  }
}
