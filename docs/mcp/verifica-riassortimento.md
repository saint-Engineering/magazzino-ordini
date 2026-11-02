# Verifica esplorativa del riassortimento (M7 · G1)

Il testo della issue che l'agente apre con GitHub MCP, dopo il giro con Playwright MCP.
I valori «atteso» vengono dal codice (`Riassortimento`, `ProdottiController`, la pagina
`riassortimento.html`); il risultato «osservato» va confermato dal vivo.

| # | Caso | Come lo prova l'agente | Atteso | Giacenza |
|---|---|---|---|---|
| 1 | 1 pezzo | form normale | redirect a `/movimenti`, «Riassortimento registrato», CARICO | +1 |
| 2 | 1000 pezzi | form normale | come sopra | +1000 |
| 3 | 0 pezzi | il browser blocca (`min="1"`); senza `min` | errore «La quantità da riassortire va da 1 a 1000» nella pagina | invariata |
| 4 | 1001 pezzi | il browser blocca (`max="1000"`); senza `max` | stesso errore | invariata |
| 5 | quantità vuota | il browser blocca (`required`); senza `required` | «Indica la quantità da riassortire» | invariata |
| 6 | quantità «abc» | solo togliendo `type="number"` | **da osservare**: il campo `Integer` non si converte e il controller non ha un `BindingResult`; ci si aspetta una pagina di errore 400 di Spring invece del messaggio in italiano | invariata |
| 7 | nota di 250 caratteri | form normale | registrato | +n |
| 8 | nota di 251 caratteri | il browser tronca (`maxlength="250"`); senza `maxlength` | «La nota può avere al massimo 250 caratteri» | invariata |

Difetto probabile da segnalare: caso 6 (errore tecnico al posto del messaggio per l'utente).
Osservazione: i casi 3, 4, 5 e 8 il browser li ferma prima del server; il server li
rifiuta comunque, ed è quello che conta.
