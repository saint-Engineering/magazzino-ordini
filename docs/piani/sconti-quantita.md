# Sconti per quantita e spedizione (M6 - G1)

## Specifica

- R1: sconto sulla quantita totale della riga: 0% da 1 a 9 pezzi, 5% da 10 a 49 e 10% da 50 in su.
- R2: calcoli con `BigDecimal`, senza arrotondamenti intermedi e arrotondamento finale al centesimo con `HALF_UP`.
- R3: aggiunte ripetute dello stesso prodotto confluiscono nella stessa riga prima di determinare lo sconto.
- R4: il totale merce e la somma dei subtotali scontati.
- R5: spedizione di 9,90 euro sotto 150,00 euro di merce scontata, gratuita da 150,00 euro; il totale pagabile somma merce e spedizione.
- R6: dettaglio con sconto per riga e riepilogo merce/spedizione/totale; elenco con totale da pagare.

Le quantita minori o uguali a zero restano invalide secondo la validazione esistente. Non si aggiungono dipendenze e non si cambia lo stack.

## Decisioni di implementazione

- Lo sconto viene calcolato dalla quantita corrente della riga, non memorizzato separatamente: cosi resta corretto quando una riga viene incrementata.
- La soglia di spedizione si applica al totale merce gia scontato, non al totale lordo; il calcolo resta nell'entita `Ordine`.
- `RigaOrdine` espone i nomi `scontoPercentuale()` e `percentualeSconto()` per mantenere compatibilita con i punti d'uso esistenti, delegando entrambi al calcolo condiviso `ScontoQuantita`.
- `Ordine` mantiene `spedizione()` e `speseSpedizione()` come nomi equivalenti per il costo di consegna.

## Interventi e verifiche previsti

1. Implementare le fasce in `ScontoQuantita`, il calcolo del subtotale scontato in `RigaOrdine` e spedizione/totale pagabile in `Ordine`.
2. Aggiornare le pagine di dettaglio ed elenco, mantenendo la formattazione italiana e due decimali.
3. Coprire i confini degli sconti, `HALF_UP`, aggiunte ripetute, totale merce, soglie 149,99/150,00 euro e valori mostrati dalle pagine.
4. Verificare anche la soglia applicata dopo lo sconto, i casi di quantita non valida e il flusso di riassortimento/movimenti.
5. Eseguire `.\mvnw.cmd test`; le verifiche di mutazione della soglia 10 → 11 sono riportate nei test solo se effettivamente eseguite.
