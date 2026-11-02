package com.epicode.magazzino.mcp;

import com.epicode.magazzino.model.Riassortimento;
import com.epicode.magazzino.service.CatalogoService;
import com.epicode.magazzino.service.OrdineService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * I tool MCP del magazzino. È un ingresso come i controller di {@code web}:
 * traduce una chiamata dell'agente in un caso d'uso dei service e restituisce
 * record pensati per il modello. Nessuna regola di business qui.
 *
 * Gli errori di dominio ({@code MagazzinoException}, una RuntimeException)
 * diventano un risultato con isError e il messaggio in italiano: il modello lo
 * legge e può correggere la chiamata.
 */
@Component
public class MagazzinoTools {

  /** Quanti prodotti al massimo in una risposta: il contesto è limitato. */
  static final int MASSIMO_PRODOTTI = 20;

  private final CatalogoService catalogo;
  private final OrdineService ordini;

  public MagazzinoTools(CatalogoService catalogo, OrdineService ordini) {
    this.catalogo = catalogo;
    this.ordini = ordini;
  }

  @McpTool(name = "cerca_prodotti", description = """
      Cerca nel catalogo i prodotti il cui codice o nome contiene il testo \
      indicato (maiuscole indifferenti, almeno 2 caratteri). Restituisce al \
      massimo 20 prodotti con prezzo, giacenza e scorta minima; 'trovati' \
      dice quanti sono in tutto.""", generateOutputSchema = true,
      annotations = @McpAnnotations(title = "Cerca prodotti",
          readOnlyHint = true, destructiveHint = false, idempotentHint = true,
          openWorldHint = false))
  public ElencoProdotti cercaProdotti(@McpToolParam(
      description = "Testo da cercare, es. 'viti' oppure 'TAS'") String testo) {
    return ElencoProdotti.di(catalogo.cerca(testo), MASSIMO_PRODOTTI);
  }

  @McpTool(name = "prodotti_sotto_scorta", description = """
      Elenca i prodotti da riassortire: giacenza uguale o inferiore alla \
      scorta minima, dal più scoperto. Usalo per le domande sulle scorte e \
      prima di proporre un riordino.""", generateOutputSchema = true,
      annotations = @McpAnnotations(title = "Prodotti sotto scorta",
          readOnlyHint = true, destructiveHint = false, idempotentHint = true,
          openWorldHint = false))
  public ElencoProdotti prodottiSottoScorta() {
    return ElencoProdotti.di(catalogo.daRiassortire(), MASSIMO_PRODOTTI);
  }

  @McpTool(name = "dettaglio_ordine", description = """
      Restituisce un ordine dato il suo numero: cliente, stato (CONFERMATO, \
      SPEDITO o ANNULLATO), righe con sconti, spese di spedizione e totale \
      da pagare. Se l'ordine non esiste restituisce un errore.""",
      generateOutputSchema = true,
      annotations = @McpAnnotations(title = "Dettaglio ordine",
          readOnlyHint = true, destructiveHint = false, idempotentHint = true,
          openWorldHint = false))
  public OrdineMcp dettaglioOrdine(@McpToolParam(
      description = "Numero dell'ordine, es. 3") long numero) {
    return OrdineMcp.da(ordini.trova(numero));
  }

  @McpTool(name = "riassortisci_prodotto", description = """
      Aggiunge pezzi alla giacenza di un prodotto e registra un movimento \
      CARICO nello storico. MODIFICA I DATI: usalo solo quando l'utente \
      chiede esplicitamente di riassortire. Quantità da 1 a 1000; nota \
      facoltativa fino a 250 caratteri (es. il numero del documento di \
      trasporto).""", generateOutputSchema = true,
      annotations = @McpAnnotations(title = "Riassortisci un prodotto",
          readOnlyHint = false, destructiveHint = false,
          idempotentHint = false, openWorldHint = false))
  public EsitoRiassortimento riassortisciProdotto(
      @McpToolParam(description = "Codice del prodotto, es. TAS-8") String codice,
      @McpToolParam(description = "Pezzi da aggiungere, da 1 a 1000") int quantita,
      @McpToolParam(description = "Nota per lo storico, facoltativa",
          required = false) String nota) {
    return EsitoRiassortimento.di(
        catalogo.riassortisci(codice, new Riassortimento(quantita, nota)),
        quantita);
  }
}
