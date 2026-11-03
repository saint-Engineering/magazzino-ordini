# I server MCP per ruolo (M7 · G2)

Spring AI 2.0.2, starter `spring-ai-starter-mcp-server-webmvc`, tre server
Streamable HTTP nella stessa applicazione. `McpServerConfig` registra trasporti,
sessioni e cataloghi distinti; i tool restano in `com.epicode.magazzino.mcp`.
Il precedente endpoint condiviso `/mcp` non è più disponibile.

| Server | Endpoint locale | Ruolo richiesto | Tool esposti |
|---|---|---|---|
| USER | `http://localhost:8080/mcp/user` | UTENTE | `cerca_prodotti`, `prodotti_sotto_scorta`, `dettaglio_ordine` |
| MAGAZZINO | `http://localhost:8080/mcp/magazzino` | MAGAZZINIERE | `cerca_prodotti`, `prodotti_sotto_scorta`, `riassortisci_prodotto`, `crea_prodotto`, `smaltisci_prodotto`, `storico_movimenti` |
| ADMIN | `http://localhost:8080/mcp/admin` | AMMINISTRATORE | `utenti_bloccati` |

| Tool | Cosa fa | Annotazioni | Errori tipici (isError) |
|---|---|---|---|
| `cerca_prodotti(testo)` | prodotti con codice o nome che contiene il testo, max 20 | sola lettura, idempotente | testo con meno di 2 caratteri |
| `prodotti_sotto_scorta()` | giacenza ≤ scorta minima, dal più scoperto, max 20 | sola lettura, idempotente | — |
| `dettaglio_ordine(numero)` | cliente, stato, righe, totali | sola lettura, idempotente | ordine inesistente |
| `riassortisci_prodotto(codice, quantita, nota?)` | aggiunge pezzi e registra un CARICO | **scrive**, non distruttivo, non idempotente | prodotto inesistente, quantità fuori 1-1000, nota > 250 |
| `crea_prodotto(codice, nome, prezzo, scortaMinima)` | censisce un prodotto con giacenza iniziale zero; prezzo al centesimo con HALF_UP | **scrive**, non distruttivo, non idempotente | codice duplicato, codice/nome vuoti, prezzo o scorta minima negativi |
| `smaltisci_prodotto(codice, quantita, nota?)` | rimuove pezzi e registra uno SMALTIMENTO | **scrive**, **distruttivo**, non idempotente | prodotto inesistente, quantità ≤ 0 o superiore alla giacenza, nota > 250 |
| `storico_movimenti()` | storico completo dal più recente, con data, prodotto, tipo, quantità, nota ed eventuale ordine | sola lettura, idempotente | — |
| `utenti_bloccati()` | account bloccati di tutti i ruoli, ordinati per username, senza credenziali | sola lettura, idempotente, solo ADMIN | — |

### Consultare gli utenti bloccati

Collegati ad ADMIN con una chiave del ruolo AMMINISTRATORE e chiama
`utenti_bloccati` senza argomenti (`{}`). Un account è bloccato quando la
regola `Utente.isAccountNonLocked()` restituisce false: attualmente dopo
almeno **3 errori di login**. La lista include account di qualsiasi ruolo,
ordinati per username, senza paginazione o tagli.

Esempio di risposta strutturata:

```json
{
  "utenti": [
    {
      "id": 42,
      "username": "esempio",
      "nome": "Nome",
      "cognome": "Cognome",
      "ruolo": "UTENTE",
      "errori": 3
    }
  ],
  "totale": 1
}
```

Il ruolo è una stringa (`AMMINISTRATORE`, `UTENTE` o `MAGAZZINIERE`).
Se non ci sono blocchi, la risposta è `{"utenti":[],"totale":0}`, non un
errore. Password, hash e API key non sono inclusi. Il tool non sblocca utenti,
non azzera errori e non modifica gli account. Dopo lo sblocco dalla pagina
Utenti, una nuova chiamata non include più quell'account.

USER e MAGAZZINO non espongono né eseguono questo tool. ADMIN continua a non
esporre prompt o tool operativi di magazzino. La consultazione non modifica
la policy di autenticazione delle API key degli account bloccati.

### Operazioni del magazziniere

Collegati a MAGAZZINO con una chiave del ruolo MAGAZZINIERE. Esempi di
argomenti per le chiamate ai tool:

```json
{"codice":"MCP-NUOVO","nome":"Nuovo articolo","prezzo":12.35,"scortaMinima":5}
```

Il censimento restituisce un `ProdottoMcp` con `disponibili: 0`; il carico
iniziale si registra separatamente con `riassortisci_prodotto`.
Per smaltire merce danneggiata:

```json
{"codice":"MCP-NUOVO","quantita":2,"nota":"Merce danneggiata"}
```

Lo smaltimento restituisce codice, nome, `smaltiti`, `disponibili` e
`daRiassortire`. Come nella pagina web, non ha il tetto di 1000 pezzi del
riassortimento: il limite è la giacenza disponibile. Una richiesta non valida
non modifica né scorte né storico. Le operazioni di scrittura vanno eseguite
solo su richiesta esplicita; le annotazioni MCP segnalano il rischio ma non
sostituiscono la conferma nel client.

`storico_movimenti` non richiede argomenti (`{}`) e restituisce
`{"movimenti":[...]}` con l'intero storico, senza tagli. Ogni riga contiene
`creatoIl`, `codice`, `nome`, `tipo`, `quantita`, `nota` e `ordineId`;
nota e ordine possono essere null. I dati restituiti sono record, mai entità JPA.
USER e ADMIN non espongono nessuno dei tre nuovi tool.

Prompt: `piano-riordino(copertura?)`, in VS Code `/USER.piano-riordino` oppure
`/MAGAZZINO.piano-riordino`: chiede una proposta di riordino usando solo
`prodotti_sotto_scorta`. ADMIN espone soltanto `utenti_bloccati`, senza
capacità prompt né relativi metodi MCP.

Regole di progetto:
- il package `mcp` chiama solo i `service` (come `web`), mai i `repository`;
- i tool restituiscono record propri (`ProdottoMcp`, `ElencoProdotti`, `OrdineMcp`,
  `EsitoRiassortimento`, `EsitoSmaltimento`, `ElencoMovimenti`, `MovimentoMcp`),
  mai entità JPA; le liste stanno dentro un oggetto;
- il tool amministrativo restituisce `ElencoUtentiBloccati` e
  `UtenteBloccatoMcp`, senza campi contenenti credenziali;
- gli errori di dominio sono `RuntimeException`: Spring AI li trasforma in un risultato con
  `isError` e il messaggio in italiano, che il modello legge;
- `spring.ai.mcp.server.enabled=false` e
  `spring.ai.mcp.server.annotation-scanner.enabled=false` disattivano il server
  unico e il catalogo automatico: la configurazione Java registra esplicitamente
  solo i tool consentiti per ciascun server.

## Autenticazione e autorizzazioni

Ogni endpoint richiede una chiave personale del ruolo corrispondente
nell'header `Authorization: Bearer <chiave>`. Alla creazione di un utente
l'applicazione scarica un file `.txt` con la nuova chiave; per gli account
iniziali l'amministratore può generarla e scaricarla dalla pagina **Utenti**.
Il segreto viene mostrato solo nel download: nel database è conservato
unicamente il suo hash SHA-256. Rigenerando la chiave quella precedente cessa
immediatamente di funzionare; la revoca disabilita l'accesso MCP per l'utente.

La chiave identifica l'utente, non un server: scegli l'endpoint del suo ruolo.
Una chiave USER non accede a MAGAZZINO o ADMIN, e una chiave amministrativa
non concede accesso ai server operativi. I tool non consentiti non sono
elencati né richiamabili, anche conoscendone il nome. Le sessioni non sono
trasferibili tra endpoint.

La policy CSRF è esclusa solo per i tre endpoint esatti; le altre rotte web mantengono le
regole esistenti. Il login nel browser non autentica il client MCP: ogni
richiesta deve includere la chiave nell'header `Authorization`.

### Leggere il file scaricato

Il file `chiave-mcp-utente-<id>.txt` contiene nella prima riga:

```text
Authorization: Bearer <CHIAVE_PERSONALE>
```

`<CHIAVE_PERSONALE>` è un segnaposto: usa il valore effettivo presente nel
file, che inizia con `mcp_`. La seconda riga è un avviso e non fa parte della
chiave. Non caricare il file intero nel client e non usare la chiave come
password del login web.

| Campo richiesto dal client | Valore da copiare |
|---|---|
| Nome dell'header | `Authorization` |
| Valore dell'header | `Bearer ` seguito dalla chiave, con un solo spazio |
| Token in un campo dedicato all'autenticazione Bearer | Solo la chiave, senza `Authorization:` e senza `Bearer ` |

### Configurare VS Code / GitHub Copilot

Il file `.vscode/mcp.json` del repository contiene già i tre server e i
relativi input segreti. Se configuri un altro client VS Code, usa queste
voci conservando gli altri server e gli eventuali input:

```json
{
  "inputs": [
    {
      "id": "mcp-user-api-key",
      "type": "promptString",
      "description": "Chiave MCP di un utente con ruolo UTENTE",
      "password": true
    },
    {
      "id": "mcp-magazzino-api-key",
      "type": "promptString",
      "description": "Chiave MCP di un utente con ruolo MAGAZZINIERE",
      "password": true
    },
    {
      "id": "mcp-admin-api-key",
      "type": "promptString",
      "description": "Chiave MCP di un utente con ruolo AMMINISTRATORE",
      "password": true
    }
  ],
  "servers": {
    "USER": {
      "type": "http",
      "url": "http://localhost:8080/mcp/user",
      "headers": {
        "Authorization": "Bearer ${input:mcp-user-api-key}"
      }
    },
    "MAGAZZINO": {
      "type": "http",
      "url": "http://localhost:8080/mcp/magazzino",
      "headers": {
        "Authorization": "Bearer ${input:mcp-magazzino-api-key}"
      }
    },
    "ADMIN": {
      "type": "http",
      "url": "http://localhost:8080/mcp/admin",
      "headers": {
        "Authorization": "Bearer ${input:mcp-admin-api-key}"
      }
    }
  }
}
```

Avvia l'applicazione, quindi avvia o riavvia solo il server del tuo ruolo dal file
MCP o dal comando **MCP: List Servers**. Quando VS Code richiede la chiave,
incolla solo il valore che inizia con `mcp_`. Il JSON contiene il riferimento
all'input, non il segreto, e può quindi essere condiviso senza includere la
chiave. Ogni input è indipendente: non inserire la stessa chiave nei tre
campi, che richiedono ruoli diversi. Dopo la connessione puoi usare i tool
del relativo catalogo; con ADMIN è disponibile solo `utenti_bloccati` e
nessun prompt. Dopo l'aggiornamento riavvia il server MCP nel client per
ricaricare il catalogo.
Rimuovi l'eventuale vecchia voce `magazzino` e la relativa configurazione
dell'input `magazzino-api-key` dai client configurati in precedenza.

### Configurare MCP Inspector

Con l'applicazione avviata, esegui `npx @modelcontextprotocol/inspector`.
Seleziona **Streamable HTTP**, imposta l'URL del ruolo dalla tabella iniziale e
configura l'header `Authorization` con valore `Bearer <CHIAVE_PERSONALE>`.
Se l'interfaccia propone invece un campo dedicato al token Bearer, inserisci
solo la chiave. Connettiti e apri l'elenco dei tool; ad esempio, con un
account Utente o Magazziniere puoi chiamare `cerca_prodotti` con
`{"testo":"viti"}`.

### Rigenerazione, revoca e problemi di connessione

Il file già scaricato non è recuperabile dal server. Se lo perdi, chiedi
all'amministratore di rigenerare la chiave: il nuovo download sostituisce la
precedente, non ne crea una copia. Aggiorna il token nel client e riconnetti
il server MCP. Se VS Code riutilizza il vecchio input, usa **MCP: Reset Cached
Inputs** e avvia nuovamente il server per inserire la nuova chiave.

| Sintomo | Controllo |
|---|---|
| HTTP `401` | Header assente, token errato, revocato o sostituito: controlla di non aver copiato l'avviso del file o duplicato il prefisso `Bearer` |
| HTTP `403` | La chiave è valida ma il ruolo non corrisponde all'endpoint: scegli USER, MAGAZZINO o ADMIN secondo la tabella |
| Tool assente o non trovato | Il catalogo è separato per ruolo: ordini solo su USER; censimento, carico, smaltimento e storico solo su MAGAZZINO; utenti bloccati solo su ADMIN, che non ha prompt. Dopo un aggiornamento riavvia il server nel client per aggiornare il catalogo |
| Connessione rifiutata o vecchio endpoint non accessibile | Avvia l'applicazione e controlla host, porta e percorso: `/mcp/user`, `/mcp/magazzino` o `/mcp/admin`, non `/mcp` |
| La chiave non funziona dopo il riavvio dell'app | Il database H2 attuale è in memoria: account creati e chiavi vengono persi a ogni riavvio; genera una nuova chiave e aggiorna il client |

Conserva il file come una password: non inserirlo nel repository, nei log o
nelle schermate condivise. Fuori da `localhost`, usa HTTPS sia per il
download sia per le chiamate MCP: il token Bearer concede accesso a chiunque
lo possieda.
