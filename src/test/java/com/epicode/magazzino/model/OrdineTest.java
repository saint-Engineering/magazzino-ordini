package com.epicode.magazzino.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrdineTest {

    private static final LocalDateTime ADESSO = LocalDateTime.of(2026, 10, 26, 9, 0);

    private Prodotto viti;
    private Prodotto trapano;

    @BeforeEach
    void catalogo() {
        viti = new Prodotto("VIT", "Viti", new BigDecimal("6.90"), 100, 10);
        trapano = new Prodotto("TRA", "Trapano", new BigDecimal("89.00"), 5, 1);
    }

    @Test
    void ilTotaleESommaDeiSubtotali() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(viti, 3);      // 20,70
        ordine.aggiungiRiga(trapano, 2);   // 178,00
        assertEquals(new BigDecimal("198.70"), ordine.totale());
    }

    @Test
    void loScontoSegueLeFasceConTreValoriIntornoAlleSoglie() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(new Prodotto("P9", "Prodotto 9", new BigDecimal("1.00"), 9, 0), 9);
        ordine.aggiungiRiga(new Prodotto("P10", "Prodotto 10", new BigDecimal("1.00"), 10, 0), 10);
        ordine.aggiungiRiga(new Prodotto("P11", "Prodotto 11", new BigDecimal("1.00"), 11, 0), 11);
        ordine.aggiungiRiga(new Prodotto("P49", "Prodotto 49", new BigDecimal("1.00"), 49, 0), 49);
        ordine.aggiungiRiga(new Prodotto("P50", "Prodotto 50", new BigDecimal("1.00"), 50, 0), 50);
        ordine.aggiungiRiga(new Prodotto("P51", "Prodotto 51", new BigDecimal("1.00"), 51, 0), 51);

        assertEquals(0, ordine.getRighe().get(0).scontoPercentuale());
        assertEquals(5, ordine.getRighe().get(1).scontoPercentuale());
        assertEquals(5, ordine.getRighe().get(2).scontoPercentuale());
        assertEquals(5, ordine.getRighe().get(3).scontoPercentuale());
        assertEquals(10, ordine.getRighe().get(4).scontoPercentuale());
        assertEquals(10, ordine.getRighe().get(5).scontoPercentuale());
    }

    @Test
    void ilSubtotaleScontatoArrotondaIlRisultatoFinaleConHalfUp() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        Prodotto prodotto = new Prodotto("ARROT", "Prodotto da arrotondare",
                new BigDecimal("0.11"), 10, 0);
        ordine.aggiungiRiga(prodotto, 10);

        assertEquals(new BigDecimal("1.05"), ordine.getRighe().get(0).subtotale());
    }

    @Test
    void quantitaNonPositiveVengonoRifiutateSenzaApplicareSconti() {
        Ordine ordine = new Ordine("Rossi", ADESSO);

        assertThrows(RichiestaNonValidaException.class, () -> ordine.aggiungiRiga(viti, 0));
        assertThrows(RichiestaNonValidaException.class, () -> ordine.aggiungiRiga(viti, -1));
        assertEquals(100, viti.getDisponibili());
        assertEquals(0, ordine.getRighe().size());
    }

    @Test
    void ogniRigaPrelevaDalMagazzino() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(trapano, 2);
        assertEquals(3, trapano.getDisponibili());
    }

    @Test
    void loStessoProdottoDueVolteFiniscenellaStessaRiga() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(viti, 3);
        ordine.aggiungiRiga(viti, 2);
        assertEquals(1, ordine.getRighe().size());
        assertEquals(5, ordine.getRighe().get(0).getQuantita());
    }

    @Test
    void scontoDelloStessoProdottoSiBasaSullaQuantitaComplessiva() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(viti, 5);
        ordine.aggiungiRiga(viti, 5);

        assertEquals(1, ordine.getRighe().size());
        assertEquals(10, ordine.getRighe().get(0).getQuantita());
        assertEquals(5, ordine.getRighe().get(0).scontoPercentuale());
        assertEquals(new BigDecimal("65.55"), ordine.getRighe().get(0).subtotale());
    }

    @Test
    void ilTotaleMerceELaSommaDeiSubtotaliScontati() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(new Prodotto("T10", "Prodotto 10", new BigDecimal("6.90"), 10, 0), 10);
        ordine.aggiungiRiga(new Prodotto("T50", "Prodotto 50", new BigDecimal("1.00"), 50, 0), 50);

        assertEquals(new BigDecimal("110.55"), ordine.totale());
    }

    @Test
    void laSpedizioneSiPagaSottoLaSogliaDiCentoECinquantaEuro() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(new Prodotto("S149", "Ordine sotto soglia",
                new BigDecimal("149.99"), 1, 0), 1);

        assertEquals(new BigDecimal("149.99"), ordine.totale());
        assertEquals(new BigDecimal("9.90"), ordine.spedizione());
        assertEquals(new BigDecimal("159.89"), ordine.totaleDaPagare());
    }

    @Test
    void laSpedizioneEGratuitaDaCentoECinquantaEuro() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(new Prodotto("S150", "Ordine a soglia",
                new BigDecimal("150.00"), 1, 0), 1);

        assertEquals(new BigDecimal("150.00"), ordine.totale());
        assertEquals(new BigDecimal("0.00"), ordine.spedizione());
        assertEquals(new BigDecimal("150.00"), ordine.totaleDaPagare());
    }

    @Test
    void ilPrezzoDellaRigaRestaQuelloDelMomentoDellOrdine() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(viti, 1);
        assertEquals(new BigDecimal("6.90"), ordine.getRighe().get(0).getPrezzoUnitario());
    }

    @Test
    void annullareRestituisceIPezzi() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(trapano, 2);
        ordine.annulla();
        assertEquals(StatoOrdine.ANNULLATO, ordine.getStato());
        assertEquals(5, trapano.getDisponibili());
    }

    @Test
    void unOrdineSpeditoNonSiAnnulla() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(trapano, 1);
        ordine.spedisci();
        assertThrows(OperazioneNonConsentitaException.class, ordine::annulla);
        assertEquals(4, trapano.getDisponibili());
    }

    @Test
    void unOrdineAnnullatoNonSiAnnullaDueVolte() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(trapano, 1);
        ordine.annulla();
        assertThrows(OperazioneNonConsentitaException.class, ordine::annulla);
        assertEquals(5, trapano.getDisponibili());
    }

    @Test
    void nonSiAggiungonoRigheAUnOrdineSpedito() {
        Ordine ordine = new Ordine("Rossi", ADESSO);
        ordine.aggiungiRiga(viti, 1);
        ordine.spedisci();
        assertThrows(OperazioneNonConsentitaException.class, () -> ordine.aggiungiRiga(viti, 1));
    }

    @Test
    void clienteObbligatorio() {
        assertThrows(RichiestaNonValidaException.class, () -> new Ordine("  ", ADESSO));
    }
}
