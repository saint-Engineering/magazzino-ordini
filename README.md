# Magazzino & Ordini

L'applicazione del **Modulo 6** del corso *Agentic Software Development*: un piccolo
gestionale di ordini e magazzino su cui lavorano gli agenti di **GitHub Copilot**
(agent mode, plan mode, cloud agent, code review).

È volutamente semplice: il protagonista del modulo non è il codice ma come lo si fa
scrivere, verificare e rivedere agli agenti.

## Stack

| | |
|---|---|
| Linguaggio | Java 25 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Data JPA) |
| Pagine | Thymeleaf (template HTML lato server) |
| Database | H2 in memoria: riparte dal catalogo iniziale a ogni avvio |
| Test | JUnit 5, Spring Boot Test, MockMvc |
| Build | Maven Wrapper (`mvnw`) |

## Avvio

```powershell
.\mvnw.cmd test              # tutti i test devono essere verdi
.\mvnw.cmd spotless:apply    # formatta il codice Java
.\mvnw.cmd verify            # test + verifica dello stile
.\mvnw.cmd spring-boot:run   # poi apri http://localhost:8080
```

## Build continua e note di rilascio

Il workflow **Build continua e prerelease** parte a **ogni push su `main`**,
senza filtri sui file. Non viene eseguito dalla sola apertura di questa PR.
Usa Temurin **JDK 25**, cache Maven e `./mvnw -B clean package`: esegue i test
e produce il JAR Spring Boot. Non cambia la versione nel `pom.xml` e non
attesta `verify`, Spotless o scansioni di sicurezza.

- **Ogni commit, non solo il tip:** da Git con cronologia completa seleziona
  tutti i commit raggiungibili da `after` ma non da `before`, in ordine
  topologico dal più vecchio. Non usa l'array `commits` del payload né una matrix:
  non ci sono troncamenti a 2048 commit o 256 job. Un merge include anche i nuovi
  commit del ramo integrato; le note del merge confrontano il **primo parent**.
  Al primo push di un nuovo `main` (`before` nullo) considera tutta la storia,
  inclusa la radice (note rispetto all'albero vuoto). Non è un backfill automatico
  della storia antecedente all'attivazione del workflow su un branch già esistente.
  Un force-push usa la stessa differenza tra grafi, non un intervallo lineare:
  non ricostruisce commit rimossi. Se il vecchio `before` non è recuperabile,
  il run fallisce esplicitamente, senza inventare un intervallo alternativo.
- **Prerelease, non versioni stabili:** per ogni SHA riuscita il publisher crea il tag
  deterministico `ci-<SHA completa>` e una prerelease **Build continua**, mai
  “Latest”. I JAR allegati hanno nomi con SHA, run e tentativo. Le note italiane
  identificano commit, messaggio, differenze, verifiche Maven e link ai run.
  Non attestano l'esecuzione di CodeQL o altre scansioni.
- **Dove leggere:** in **Actions → Build continua e prerelease** il riepilogo
  elenca l'esito di ciascuna SHA. L'artefatto
  `build-<SHA del push>-<run>-<tentativo>` conserva per 14 giorni cartelle per SHA
  con JAR riusciti, `maven.log`, report XML e `stati.json`. In **Releases** si
  trovano note e JAR permanenti delle prerelease pubblicate.
- **Errori e rerun:** un errore non interrompe le altre build; il job resta rosso
  e il publisher separato pubblica soltanto i successi. Usare **Re-run all jobs**
  sul run originale: le SHA già pubblicate vengono saltate senza cambiare
  tag, note o allegati; le bozze lasciate da upload interrotti vengono riprese.
  Il namespace `ci-` è riservato al workflow: non spostare i tag né convertire
  queste prerelease in release stabili. Tentativi concorrenti usano allegati
  distinti, sempre della stessa SHA, e le note collegano i relativi run.
- **Limiti espliciti:** elaborazione sequenziale, massimo 20 minuti per commit.
  Dopo 5 ore non avvia altre build e segnala le SHA ancora “da elaborare”,
  lasciando tempo per caricare i risultati (limite job: 6 ore).
  Rieseguire tutti i job per proseguire; timeout, indisponibilità del runner/API
  o cancellazioni manuali possono richiedere intervento/rerun. Non ci sono
  cancellazioni automatiche o gruppi concurrency che sostituiscano push in attesa.
- **Permessi:** build con `contents: read`, token non passato a Maven e credenziali
  checkout non persistite. Solo il job di pubblicazione ha `contents: write`;
  scarica dati e JAR ma non esegue il codice costruito. Nessuna credenziale esterna.
  Un amministratore deve consentire GitHub Actions e il token di scrittura per
  tag/release; eventuali regole sui tag devono ammettere `ci-*`. Per workflow
  introdotti da Copilot/bot può essere necessaria l'approvazione iniziale
  **Approve and run workflows**. Creare o unire la PR non prova che le build o
  le release siano già state eseguite: controllare i run effettivi.

**Vincolo GitHub da risolvere prima di considerare completo il requisito “ogni
commit”:** per commit storici i cui file `.github/workflows/` differiscono dal
branch predefinito, GitHub può richiedere l'autorizzazione `Workflows: write`
anche per creare/pubblicare una release. Il `GITHUB_TOKEN` non può riceverla;
neppure creare prima il tag è una soluzione garantita. Questa implementazione
non introduce credenziali esterne: un rifiuto resta un errore visibile, gli altri
commit vengono comunque elaborati e i JAR riusciti restano negli artefatti.
Un rerun non risolve un diniego permanente di autorizzazione. Non è quindi
garantita la prerelease di **ogni** SHA con i vincoli attuali: serve una decisione
del manutentore sui requisiti/autorizzazioni.
Riferimenti: [creazione e aggiornamento release](https://docs.github.com/en/rest/releases/releases#create-a-release)
e [permessi Workflows per Git refs](https://docs.github.com/en/rest/authentication/permissions-required-for-github-apps#repository-permissions-for-workflows).

I test locali della pipeline, senza rete né dipendenze aggiuntive, si eseguono con
`python3 -m unittest discover -s .github/tests -v`.

## Scansioni di sicurezza

- **CodeQL** (`.github/workflows/codeql.yml`): analizza Java con JDK 25 e autobuild
  su push e pull request verso `main`, ogni lunedì alle 05:23 UTC e su avvio manuale.
- **Dependabot** (`.github/dependabot.yml`): controlla settimanalmente Maven e le
  GitHub Actions in `.github/workflows`, con massimo 5 PR di versione aperte per
  ecosistema. Per le Actions, GitHub richiede `directory: "/"`.

### Impostazioni richieste e verifica

Un amministratore deve verificare **Settings → Security & analysis** (oppure le
sezioni **Advanced Security / Code security / Secret Protection**, secondo la UI):

1. Per usare questo workflow, passare CodeQL da **Default setup** a **Advanced
   setup**, se il primo è attivo: il default setup non accetta i risultati del
   workflow avanzato. Consentire GitHub Actions e il caricamento dei risultati
   con `security-events: write`.
2. Abilitare **Dependency graph**, **Dependabot alerts** e **Dependabot security
   updates**. Il file YAML abilita gli aggiornamenti di versione dopo il merge su
   `main`, ma non abilita questi interruttori. Gli aggiornamenti di sicurezza
   partono dagli advisory, senza aspettare la cadenza settimanale.
3. Abilitare **Secret scanning** e **Push protection**, dove disponibili:
   non si abilitano con un workflow o con `dependabot.yml`.

Il repository è pubblico: CodeQL e secret scanning sono disponibili gratuitamente
per i repository pubblici. Per repository privati/interni possono essere necessarie
licenze **GitHub Code Security** e **GitHub Secret Protection**, oltre ai permessi
di amministrazione e all'autorizzazione dell'organizzazione. Durante questa
configurazione l'integrazione ha ricevuto `403 Resource not accessible by
integration` leggendo gli alert CodeQL e secret scanning; il browser non era
autenticato. Quindi lo stato di secret scanning/push protection e gli interruttori
non sono stati verificati né modificati.

Per verificare dopo il merge:

- In **Actions → CodeQL**, controllare un'esecuzione su `main` o selezionare
  **Run workflow**; verificare che inizializzazione, autobuild e analisi siano verdi.
  Le PR verso `main` devono mostrare il controllo CodeQL (può servire l'approvazione
  di un manutentore per workflow provenienti da fork o creati da bot).
- In **Security → Code scanning**, verificare l'ultima analisi Java e gli eventuali
  alert; in **Security → Dependabot**, verificare gli alert e le PR di sicurezza.
  In **Insights → Dependency graph → Dependabot**, controllare i job settimanali
  Maven e GitHub Actions.
- In **Security → Secret scanning**, verificare che il servizio sia attivo e
  consultare gli eventuali alert. Nessun alert non significa che il servizio sia
  disabilitato; non inserire credenziali reali per provarlo.

## Come è fatto

```
src/main/java/com/epicode/magazzino
├── model/        entità e regole di business (Prodotto, Ordine, RigaOrdine, StatoOrdine, eccezioni)
├── repository/   Spring Data JPA
├── service/      casi d'uso transazionali (CatalogoService, OrdineService, NuovoOrdine)
├── web/          controller Thymeleaf, form, gestione errori
└── config/       orologio (Clock) e dati iniziali
src/main/resources/templates   le pagine Thymeleaf
src/test/java                  test di dominio (senza Spring) e di integrazione
```

Il flusso di una richiesta: **controller → service → model/repository**. Il controller
non contiene regole; le regole stanno nel model e i service le orchestrano in una
transazione.

## Regole di business già presenti

- Un ordine nasce **CONFERMATO**; può diventare **SPEDITO** oppure **ANNULLATO**, nient'altro.
- Ogni riga preleva i pezzi dal magazzino; se non bastano l'ordine non viene creato.
- Lo stesso prodotto inserito due volte finisce nella stessa riga.
- Il prezzo della riga è quello del prodotto al momento dell'ordine.
- Annullare restituisce i pezzi al magazzino; un ordine spedito non si annulla.
- Importi in `BigDecimal`, arrotondati al centesimo.
- Il ruolo **Magazziniere (2)** può censire prodotti (con giacenza iniziale zero),
  registrare carichi e smaltire scorte fuori assortimento; ogni variazione è tracciata
  nei movimenti. Utenti e amministratori non possono effettuare queste operazioni.

## Usarlo come template (docente)

1. Pubblica il repo su GitHub e attiva **Settings → Template repository**.
2. Ogni studente: **Use this template → Create a new repository**, proprietario il
   proprio account, visibilità **Public** (serve per CodeQL e secret scanning gratuiti
   nella giornata 3), senza "Include all branches".
3. Il branch `soluzione/m6-g1` e i tag restano nel repo del docente: un repository
   creato dal template parte dal solo `main`.

Lo scheletro **non** contiene `.github/copilot-instructions.md`: scriverlo è il primo
esercizio della giornata 1.

## Giornate

| Giornata | Consegna |
|---|---|
| M6 · G1 — Feature authoring e test generation | [docs/corso/M6-G1.md](docs/corso/M6-G1.md) · soluzione: branch `soluzione/m6-g1`, tag `m6-g1-end` |
