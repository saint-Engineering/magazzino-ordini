# Sicurezza

## Segnalare una vulnerabilità

Non aprire una issue pubblica. Usa **Security and quality → Report a vulnerability** di questo
repository (private vulnerability reporting): la segnalazione resta visibile solo ai
manutentori finché non è corretta.

Indica: cosa hai trovato, come riprodurlo, quale impatto immagini. Rispondiamo entro
tre giorni lavorativi.

## Cosa controlliamo in automatico

- **CodeQL** (code scanning, default setup) su ogni push e pull request verso `main`.
- **Dependabot**: alert e PR di sicurezza sulle dipendenze Maven, PR settimanali di
  aggiornamento (`.github/dependabot.yml`).
- **Secret scanning** con push protection.

## Registro delle vulnerabilità corrette

| Id | Dove | Problema | Correzione | Test d'attacco |
|---|---|---|---|---|
| V1 | `ReportController.ordini` | SQL injection: il cliente concatenato nella query | query derivata `findByClienteOrderByCreatoIlDesc` | `ReportSicurezzaTest.sqlInjectionNonRestituisceGliOrdiniDegliAltri` |
| V2 | `report.html` | XSS: titolo con `th:utext` e nome del cliente dentro | `th:text`, nessun HTML costruito in Java | `ReportSicurezzaTest.ilNomeDelClienteNonDiventaHtml` |
| V3 | `ReportController.file` | path traversal: `../` nel nome del file | `NomeFileReport`: nome ammesso + controllo `startsWith` | `NomeFileReportTest`, `ReportSicurezzaTest.pathTraversalRifiutato` |
| V4 | `ReportController.intestazione` | `StringSubstitutor.createInterpolator()` su input dell'utente (commons-text 1.9, CVE-2022-42889) | interpolatore eliminato, dipendenza tolta | `ReportServiceTest`, `ReportSicurezzaTest.lIntestazioneNonEsegueEspressioni` |
| V5 | `ReportController` | password FTP nel codice | rimossa; un segreto arriva da variabile d'ambiente o da un secret store | revisione manuale: nessuno strumento l'ha segnalata |
| V6 | `ReportController` | `JdbcTemplate` nel controller (architettura) | `ReportService` + repository | review |
