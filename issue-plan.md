# Piano di implementazione — Gestione utenti

## Obiettivo e perimetro

Implementare l'autenticazione, l'autorizzazione e le funzioni utenti richieste
nella issue #1, mantenendo lo stack e l'architettura esistenti: Spring MVC,
Thymeleaf, Spring Data JPA e H2. Il flusso attuale è
controller → service → model/repository; la sicurezza deve proteggere le pagine
esistenti oltre a quelle nuove.

La matrice dei permessi da seguire è:

- **Utente autenticato:** tutte le funzioni applicative già esistenti e cambio
  della propria password.
- **Amministratore autenticato:** solo elenco, aggiunta, modifica, eliminazione
  e sblocco utenti, oltre al cambio della propria password.
- **Utente non autenticato:** solo login; ogni altra funzione richiede
  autenticazione e reindirizza al login.

La issue menziona il cambio password sia tra le funzioni amministrative sia tra
quelle disponibili a tutti gli utenti autenticati. Interpretare la regola più
specifica come accesso consentito a entrambi i ruoli, sempre solo per la propria
utenza.

## Sequenza di lavoro

1. **Ricognizione e baseline**
   - Leggere `.github/copilot.instructions.md`, `README.md`, `pom.xml` e le
     istruzioni applicabili ai test.
   - Esaminare `MagazzinoApplication`, `DatiIniziali`, le entità e i repository,
     i service, i controller, i template e i test esistenti.
   - Controllare `git status` senza sovrascrivere modifiche preesistenti; eseguire
     `./mvnw test` e annotare eventuali errori di baseline.
   - Mappare tutti gli endpoint esistenti: nessuno deve rimanere accessibile
     senza autenticazione per omissione.

2. **Progettazione e decisioni**
   - Definire le rotte e le autorizzazioni per login, funzioni esistenti,
     amministrazione utenti e cambio password.
   - Riutilizzare i pattern di controller/service/repository esistenti; mantenere
     la logica e le transazioni nei service, non nei template.
   - Verificare le funzionalità di sicurezza già disponibili nello stack prima di
     implementare l'autenticazione. Se serve una nuova dipendenza, spiegarne lo
     scopo e chiedere conferma prima di aggiungerla, secondo le istruzioni del
     repository.
   - Usare un mapping persistente coerente con H2: `ruolo` ammette solo `0`
     (amministratore) e `1` (utente), `username` è univoco e `errori` parte da
     zero. Verificare il DDL effettivo invece di copiare alla cieca tipi
     specifici di Oracle come `varchar2`.

3. **Modello, persistenza e dati iniziali**
   - Aggiungere l'entità utente, il ruolo e il repository con vincoli coerenti
     con la tabella UTENTI richiesta.
   - Memorizzare esclusivamente hash BCrypt; verificare le password tramite il
     componente BCrypt, senza confrontare o conservare password in chiaro.
   - Inizializzare gli account di test richiesti dalla issue con password
     BCrypt, limitando l'uso delle credenziali iniziali all'ambiente di test.
     Non inserirle nei log, nei template o in documentazione aggiuntiva.
   - Aggiungere test di persistenza per unicità dello username, valori iniziali
     e vincolo sui ruoli.

4. **Autenticazione e protezione delle pagine**
   - Implementare login/logout e proteggere tutte le rotte applicative; rendere
     pubbliche solo le risorse indispensabili al login.
   - Dopo credenziali errate mostrare esattamente `Autenticazione negata`.
     Non rivelare se username o password siano quelli errati.
   - Per username esistente incrementare `errori` a ogni tentativo fallito; a
     partire da `errori >= 3` impedire il login anche con password corretta e
     mostrare `Notifica inviata alla polizia postale`.
   - Dopo autenticazione riuscita riportare `errori` a zero. Garantire che
     aggiornamento del contatore e controllo di blocco siano coerenti anche in
     caso di richieste concorrenti.
   - Aggiungere test web di accesso non autenticato, credenziali valide/non
     valide, blocco al terzo errore, tentativo su account bloccato e reset del
     contatore al login riuscito.

5. **Autorizzazione e amministrazione**
   - Consentire agli utenti di accedere alle funzionalità esistenti e agli
     amministratori esclusivamente alle funzioni amministrative e al cambio
     della propria password.
   - Implementare elenco, aggiunta, modifica di username/nome/cognome/ruolo,
     eliminazione e sblocco (`errori = 0`).
   - Validare input lato server e gestire username duplicati senza errori
     imprevisti o perdita di dati.
   - Impedire l'eliminazione dell'ultimo amministratore, anche nel service
     invocato fuori dal normale flusso web.
   - Aggiungere test service e MockMvc per ogni operazione, permessi negati,
     dati non validi, username duplicato e tentativo di eliminare l'ultimo
     amministratore.

6. **Cambio password e regole**
   - Rendere il cambio password disponibile a entrambi i ruoli autenticati, solo
     per l'account corrente.
   - Richiedere password attuale, nuova password e conferma; verificare
     l'attuale con BCrypt e rifiutare conferma diversa.
   - Accettare solo caratteri ASCII stampabili (codici 32–126) e lunghezze da
     8 a 128 caratteri inclusi.
   - Sostituire la password solo dopo tutte le verifiche e salvare un nuovo hash
     BCrypt. Testare limiti, caratteri non ammessi, password attuale errata e
     conferma non coincidente.

7. **Logging e interfaccia**
   - Registrare ogni login fallito a livello WARN con username, indirizzo IP e
     timestamp; registrare ogni login riuscito a livello INFO con username e
     timestamp. Non registrare mai password, hash o dati di autenticazione.
   - Non fidarsi di header IP inoltrati da client non attendibili; usare
     configurazione proxy fidata solo se già prevista dall'applicazione.
   - Aggiungere pagine/form di login, gestione utenti e cambio password
     coerenti con i template esistenti; mostrare errori di validazione senza
     includere password o hash nella risposta.
   - Verificare che gli utenti non vedano le funzioni amministrative e che i
     link mostrati corrispondano ai permessi effettivi lato server.

8. **Verifica finale**
   - Eseguire i test mirati durante lo sviluppo, poi `./mvnw test` e
     `./mvnw verify` per includere il controllo Spotless.
   - Avviare l'applicazione e verificare manualmente login, autorizzazioni,
     blocco/sblocco, cambio password e accesso alle pagine preesistenti.
   - Rivedere il diff e cercare password in chiaro, hash o credenziali nei log,
     template, fixture e configurazioni non di test.
   - Riportare file modificati, test eseguiti e risultati; non dichiarare
     verifiche non eseguite e non creare commit, push o PR senza richiesta.

## Criteri di completamento

- Tutte le rotte applicative richiedono autenticazione, salvo login e risorse
  strettamente necessarie.
- Ruoli e accessi corrispondono alla matrice sopra; l'autorizzazione è applicata
  lato server.
- Password salvate solo come BCrypt; validazione, contatore errori, blocco,
  sblocco, reset e logging rispettano la issue #1.
- Non è possibile eliminare l'ultimo amministratore.
- Test automatici coprono i casi positivi, negativi e i confini di blocco e
  validazione; test e verifica di stile terminano con successo.
