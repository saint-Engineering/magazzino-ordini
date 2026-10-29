package com.epicode.magazzino.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * L'intestazione non interpreta più espressioni: era la porta per Text4Shell.
 */
class ReportServiceTest {

  private final Clock ventottoOttobre = Clock
      .fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Rome"));
  private final ReportService report = new ReportService(null, "report",
      ventottoOttobre);

  @Test
  void ilSegnapostoDataVieneSostituito() {
    assertEquals("Report del 28/10/2026",
        report.intestazione("Report del {data}"));
  }

  @Test
  void leEspressioniRestanoTestoNormale() {
    assertEquals("${script:javascript:java.lang.Runtime.getRuntime()}", report
        .intestazione("${script:javascript:java.lang.Runtime.getRuntime()}"));
    assertEquals("${sys:user.home}", report.intestazione("${sys:user.home}"));
  }

  @Test
  void intestazioneStoricaUsaIlClockConFormatiDataEspliciti() {
    assertEquals("Report 2026 del 28/10/2026 ${env:PATH}",
        report.intestazioneStorica(
            "Report ${date:yyyy} del ${date:dd/MM/yyyy} ${env:PATH}"));
  }
}
