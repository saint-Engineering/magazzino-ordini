# Il protocollo MCP visto con l'Inspector (M7 · G1)

Soluzione di riferimento. Messaggi catturati il 30/10/2026 con MCP Inspector 2.13.0 e
`@modelcontextprotocol/server-everything` 2.1.0 su stdio (abbreviati dove indicato con `…`).

## initialize
- protocolVersion: `2025-11-25` (l'Inspector la propone, il server la accetta)
- capabilities del server: `tools`, `resources` (con `subscribe`), `prompts`, `logging`,
  `completions`, ognuna con `listChanged`
- serverInfo: `mcp-servers/everything`, «Everything Reference Server», versione `2.1.0`
- in più il server manda `instructions`: un testo per il modello che il client mette nel
  contesto. È utile, ma è anche testo scritto da altri che il modello leggerà.

Dopo la risposta il client manda la notifica `notifications/initialized` (senza `id`:
nessuna risposta attesa). Solo dopo partono `tools/list` e le chiamate.

## Un tool: echo
- descrizione: «Echoes back the input string»
- inputSchema: oggetto con `message` (string) obbligatorio, JSON Schema 2020-12
- annotations: `readOnlyHint: true`, `destructiveHint: false`, `idempotentHint: true`,
  `openWorldHint: false`

## Una chiamata e la sua risposta
```json
{"jsonrpc":"2.0","id":3,"method":"tools/call",
 "params":{"name":"echo","arguments":{"message":"ciao"}}}
```
```json
{"jsonrpc":"2.0","id":3,
 "result":{"content":[{"type":"text","text":"Echo: ciao"}]}}
```

## Due modi di sbagliare
| Caso | Cosa torna | Chi lo vede |
|---|---|---|
| get-sum con a = "x" | `result` con `isError: true` e il testo «Input validation error: … a: Invalid input: expected number, received string» | il **modello**: legge l'errore e può correggere la chiamata |
| tool che non esiste | `error` JSON-RPC, codice `-32602`, «Tool nonesiste not found» | il **client**: è un errore di protocollo, non un risultato del tool |

## I tool in VS Code
Da contare dal vivo: il numero dipende dalla versione dei server.

| Server | Tool visibili | Con /readonly |
|---|---|---|
| github (toolset repos, issues, pull_requests) | (contali) | spariscono i tool che scrivono: `create_issue`, `create_pull_request`, `merge_pull_request`, … |
| playwright 0.0.83 | (contali) | — |

Nota: la versione `2026-07-28` della specifica toglie `initialize` (ogni richiesta porta
versione e capability in `_meta`, il server espone `server/discover`). L'Inspector 2.13 e
l'SDK Java che useremo lunedì parlano ancora `2025-11-25`.
