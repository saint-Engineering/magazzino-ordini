# I server MCP per ruolo (M7 · G2)

Spring AI 2.0.2, starter `spring-ai-starter-mcp-server-webmvc`, tre server
Streamable HTTP nella stessa applicazione. `McpServerConfig` registra trasporti,
sessioni e cataloghi distinti; i tool restano in `com.epicode.magazzino.mcp`.
Il precedente endpoint condiviso `/mcp` non è più disponibile.

| Server | Endpoint locale | Ruolo richiesto | Tool esposti |
|---|---|---|---|
| USER | `http://localhost:8080/mcp/user` | UTENTE | `cerca_prodotti`, `prodotti_sotto_scorta`, `dettaglio_ordine` |
| MAGAZZINO | `http://localhost:8080/mcp/magazzino` | MAGAZZINIERE | `cerca_prodotti`, `prodotti_sotto_scorta`, `riassortisci_prodotto` |
| ADMIN | `http://localhost:8080/mcp/admin` | AMMINISTRATORE | nessuno |

| Tool | Cosa fa | Annotazioni | Errori tipici (isError) |
|---|---|---|---|
| `cerca_prodotti(testo)` | prodotti con codice o nome che contiene il testo, max 20 | sola lettura, idempotente | testo con meno di 2 caratteri |
| `prodotti_sotto_scorta()` | giacenza ≤ scorta minima, dal più scoperto, max 20 | sola lettura, idempotente | — |
| `dettaglio_ordine(numero)` | cliente, stato, righe, totali | sola lettura, idempotente | ordine inesistente |
| `riassortisci_prodotto(codice, quantita, nota?)` | aggiunge pezzi e registra un CARICO | **scrive**, non distruttivo, non idempotente | prodotto inesistente, quantità fuori 1-1000, nota > 250 |

Prompt: `piano-riordino(copertura?)`, in VS Code `/USER.piano-riordino` oppure
`/MAGAZZINO.piano-riordino`: chiede una proposta di riordino usando solo
`prodotti_sotto_scorta`. ADMIN permette l'inizializzazione e il ping, ma non
annuncia capacità tool/prompt e non registra i relativi metodi MCP.

Regole di progetto:
- il package `mcp` chiama solo i `service` (come `web`), mai i `repository`;
- i tool restituiscono record propri (`ProdottoMcp`, `ElencoProdotti`, `OrdineMcp`,
  `EsitoRiassortimento`), mai entità JPA; le liste stanno dentro un oggetto;
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
del relativo catalogo; con ADMIN è normale non vedere tool o prompt.
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
| Tool assente o non trovato | Il catalogo è separato per ruolo: ordini solo su USER, riassortimenti solo su MAGAZZINO; ADMIN non espone tool né prompt |
| Connessione rifiutata o vecchio endpoint non accessibile | Avvia l'applicazione e controlla host, porta e percorso: `/mcp/user`, `/mcp/magazzino` o `/mcp/admin`, non `/mcp` |
| La chiave non funziona dopo il riavvio dell'app | Il database H2 attuale è in memoria: account creati e chiavi vengono persi a ogni riavvio; genera una nuova chiave e aggiorna il client |

Conserva il file come una password: non inserirlo nel repository, nei log o
nelle schermate condivise. Fuori da `localhost`, usa HTTPS sia per il
download sia per le chiamate MCP: il token Bearer concede accesso a chiunque
lo possieda.
