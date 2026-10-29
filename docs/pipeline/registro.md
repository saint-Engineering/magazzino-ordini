# Registro della pipeline (M6 · G4)

Una riga per tappa. Gli orari dalla timeline della issue e della PR; i costi da
**Settings → Billing** (AI credits e minuti di GitHub Actions) se li vedi, altrimenti
"non visibile".

| Tappa | Chi | Inizio | Fine | Esito | Intervento umano |
|---|---|---|---|---|---|
| Issue (analista + modulo) | agente analista + io | | | | |
| Implementazione | cloud agent, `sviluppatore-magazzino` | | | | |
| Build e test | Actions, job `build` | | | | |
| CodeQL | code scanning | | | | |
| Code review | Copilot | | | | |
| Iterazioni `@copilot` | cloud agent | | | | |
| Review e merge | io | | | | |
| Tag e release in bozza | Actions, job `release` | | | | |

## Domande per il debrief

1. Quale tappa ha trovato il problema più grave? Quale non ha trovato niente?
2. Dove hai dovuto intervenire tu, e la regola che ti ha costretto era scritta?
3. Questa feature valeva una pipeline di agenti, o bastava Agent in VS Code?
