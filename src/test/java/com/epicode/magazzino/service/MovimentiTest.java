package com.epicode.magazzino.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.epicode.magazzino.model.MovimentoMagazzino;
import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.model.Riassortimento;
import com.epicode.magazzino.model.RichiestaNonValidaException;
import com.epicode.magazzino.model.ScortaInsufficienteException;
import com.epicode.magazzino.model.Smaltimento;
import com.epicode.magazzino.model.TipoMovimento;
import com.epicode.magazzino.repository.ProdottoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** R3-R6 con service, repository e H2 veri. */
@SpringBootTest
@Transactional
class MovimentiTest {

    @Autowired
    private CatalogoService catalogo;

    @Autowired
    private OrdineService ordini;

    @Autowired
    private ProdottoRepository prodotti;

    @BeforeEach
    void prodottoDiTest() {
        prodotti.save(new Prodotto("T-MOV", "Prodotto per i movimenti", new BigDecimal("1.00"), 10, 2));
    }

    private List<MovimentoMagazzino> movimentiDi(String codice) {
        return catalogo.movimenti().stream()
                .filter(m -> m.getProdotto().getCodice().equals(codice))
                .toList();
    }

    @Test
    void ilRiassortimentoAumentaLaGiacenzaERegistraUnCarico() {
        catalogo.riassortisci("T-MOV", new Riassortimento(25, "Consegna fornitore"));

        assertEquals(35, catalogo.trova("T-MOV").getDisponibili());
        List<MovimentoMagazzino> movimenti = movimentiDi("T-MOV");
        assertEquals(1, movimenti.size());
        assertEquals(TipoMovimento.CARICO, movimenti.get(0).getTipo());
        assertEquals(25, movimenti.get(0).getQuantita());
        assertEquals("Consegna fornitore", movimenti.get(0).getNota());
    }

    @Test
    void censisceUnNuovoProdottoConGiacenzaInizialeVuota() {
        Prodotto prodotto = catalogo.crea("T-NEW", "Nuovo prodotto",
                new BigDecimal("4.25"), 3);

        assertEquals("T-NEW", prodotto.getCodice());
        assertEquals(new BigDecimal("4.25"), prodotto.getPrezzo());
        assertEquals(0, prodotto.getDisponibili());
        assertEquals(3, prodotto.getScortaMinima());
    }

    @Test
    void nonCensisceDueProdottiConLoStessoCodice() {
        assertThrows(RichiestaNonValidaException.class,
                () -> catalogo.crea("T-MOV", "Duplicato",
                        new BigDecimal("1.00"), 0));
    }

    @Test
    void smaltisceScorteERegistraIlMovimentoSenzaOrdine() {
        catalogo.smaltisci("T-MOV", new Smaltimento(4, "Prodotto cessato"));

        assertEquals(6, catalogo.trova("T-MOV").getDisponibili());
        List<MovimentoMagazzino> movimenti = movimentiDi("T-MOV");
        assertEquals(1, movimenti.size());
        assertEquals(TipoMovimento.SMALTIMENTO, movimenti.get(0).getTipo());
        assertEquals(4, movimenti.get(0).getQuantita());
        assertEquals("Prodotto cessato", movimenti.get(0).getNota());
        assertNull(movimenti.get(0).getOrdineId());
    }

    @Test
    void smaltimentoOltreLaGiacenzaNonCambiaGiacenzaENonRegistraMovimenti() {
        assertThrows(ScortaInsufficienteException.class,
                () -> catalogo.smaltisci("T-MOV", new Smaltimento(11, null)));

        assertEquals(10, catalogo.trova("T-MOV").getDisponibili());
        assertEquals(0, movimentiDi("T-MOV").size());
    }

    @Test
    void unaQuantitaFuoriLimiteNonArrivaAlService() {
        assertThrows(RichiestaNonValidaException.class, () -> new Riassortimento(1001, null));
        assertEquals(10, catalogo.trova("T-MOV").getDisponibili());
        assertEquals(0, movimentiDi("T-MOV").size());
    }

    @Test
    void unOrdineScaricaEAnnullandoloRientra() {
        Ordine ordine = ordini.crea(new NuovoOrdine("Bianchi", List.of(new NuovoOrdine.Riga("T-MOV", 4))));
        ordini.annulla(ordine.getId());

        List<MovimentoMagazzino> movimenti = movimentiDi("T-MOV");
        assertEquals(2, movimenti.size());
        // R6: dal più recente
        assertEquals(TipoMovimento.RESO, movimenti.get(0).getTipo());
        assertEquals(TipoMovimento.SCARICO, movimenti.get(1).getTipo());
        assertEquals(ordine.getId(), movimenti.get(0).getOrdineId());
        assertEquals(4, movimenti.get(1).getQuantita());
        assertEquals(10, catalogo.trova("T-MOV").getDisponibili());
    }
}
