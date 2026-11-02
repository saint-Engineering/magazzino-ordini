# Server MCP ammessi nel progetto (M7 · G1)

Soluzione di riferimento. Decisione: **ammesso**, **ammesso con limiti** o **rifiutato**.

| Criterio | github | playwright | filesystem (esempio di terzo server) |
|---|---|---|---|
| Chi lo pubblica, dove sta il sorgente | GitHub, `github/github-mcp-server` | Microsoft, `microsoft/playwright-mcp` | progetto MCP, `modelcontextprotocol/servers` |
| Locale (stdio) o remoto (http) | remoto, `api.githubcopilot.com/mcp/` | locale, processo `npx` sul PC, profilo `--isolated` | locale, `npx` |
| Versione fissata? | no: è un servizio, cambia senza di noi | sì, `@playwright/mcp@0.0.83` | da fissare (`@2.1.0`) |
| Credenziali: quali, dove stanno | OAuth di VS Code, nessun token nel file | nessuna | nessuna |
| Tool che scrivono o cancellano | issue, PR, merge, file nel repo | clic e invio di form nel browser | scrittura e spostamento di file |
| Dati che escono dal PC | sì, verso GitHub (codice e issue del repo) | no, se resta su localhost | no |
| Contenuti esterni che il modello leggerà | testo di issue e PR, scritto da chiunque abbia accesso | le pagine visitate | i file delle cartelle concesse |
| Decisione e limiti | **ammesso con limiti**: solo i toolset `repos,issues,pull_requests`; `/readonly` quando non serve scrivere; ogni scrittura approvata a mano | **ammesso con limiti**: solo `localhost`, versione fissata | **rifiutato** per oggi: non serve, e Agent ha già i suoi strumenti sui file del workspace |

Regola del progetto: un server entra in `.vscode/mcp.json` solo con una scheda qui sopra e
una PR rivista.
