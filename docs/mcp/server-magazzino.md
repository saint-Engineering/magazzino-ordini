# Il server MCP del magazzino (M7 · G2)

Spring AI 2.0.2, starter `spring-ai-starter-mcp-server-webmvc`, Streamable HTTP su
`http://localhost:8080/mcp` mentre l'app è avviata. Codice in `com.epicode.magazzino.mcp`.

| Tool | Cosa fa | Annotazioni | Errori tipici (isError) |
|---|---|---|---|
| `cerca_prodotti(testo)` | prodotti con codice o nome che contiene il testo, max 20 | sola lettura, idempotente | testo con meno di 2 caratteri |
| `prodotti_sotto_scorta()` | giacenza ≤ scorta minima, dal più scoperto, max 20 | sola lettura, idempotente | — |
| `dettaglio_ordine(numero)` | cliente, stato, righe, totali | sola lettura, idempotente | ordine inesistente |
| `riassortisci_prodotto(codice, quantita, nota?)` | aggiunge pezzi e registra un CARICO | **scrive**, non distruttivo, non idempotente | prodotto inesistente, quantità fuori 1-1000, nota > 250 |

Prompt: `piano-riordino(copertura?)`, in VS Code `/magazzino.piano-riordino`: chiede
una proposta di riordino usando solo `prodotti_sotto_scorta`.

Regole di progetto:
- il package `mcp` chiama solo i `service` (come `web`), mai i `repository`;
- i tool restituiscono record propri (`ProdottoMcp`, `ElencoProdotti`, `OrdineMcp`,
  `EsitoRiassortimento`), mai entità JPA; le liste stanno dentro un oggetto;
- gli errori di dominio sono `RuntimeException`: Spring AI li trasforma in un risultato con
  `isError` e il messaggio in italiano, che il modello legge;
- `spring.ai.mcp.server.protocol=STREAMABLE` va scritto: senza, Spring AI 2.0 attiva il
  vecchio trasporto SSE.

Prova senza Copilot: con l'app avviata,
`npx @modelcontextprotocol/inspector`, transport **Streamable HTTP**, URL
`http://localhost:8080/mcp`.

L'endpoint non ha autenticazione: va bene solo sul tuo PC. Domani: come darlo al cloud
agent senza aprirlo a tutti.
