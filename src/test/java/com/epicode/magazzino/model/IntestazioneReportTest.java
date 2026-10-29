package com.epicode.magazzino.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IntestazioneReportTest {

  private static final LocalDate DATA = LocalDate.of(2026, 10, 28);

  @Test
  void sostituisceSoloDateConFormatiDistinti() {
    assertEquals(
        "Report 2026 del 28/10/2026 ${env:PATH} ${sys:user.home} {data}",
        IntestazioneReport.sostituisciData(
            "Report ${date:yyyy} del ${date:dd/MM/yyyy} ${env:PATH} ${sys:user.home} {data}",
            DATA));
  }

  @Test
  void rifiutaUnFormatoDataNonValido() {
    assertThrows(RichiestaNonValidaException.class,
        () -> IntestazioneReport.sostituisciData("${date:invalid}", DATA));
  }

  @Test
  void conservaCaratteriSpecialiSenzaEscapingDiPresentazione() {
    assertEquals("<b>2026</b> & $",
        IntestazioneReport.sostituisciData("<b>${date:yyyy}</b> & $", DATA));
  }
}
