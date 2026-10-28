package com.epicode.magazzino.service;

import com.epicode.magazzino.model.MovimentoMagazzino;
import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.model.Riassortimento;
import com.epicode.magazzino.model.RichiestaNonValidaException;
import com.epicode.magazzino.model.RisorsaNonTrovataException;
import com.epicode.magazzino.model.Smaltimento;
import com.epicode.magazzino.repository.MovimentoRepository;
import com.epicode.magazzino.repository.ProdottoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogoService {

    private final ProdottoRepository prodotti;
    private final MovimentoRepository movimenti;
    private final Clock clock;

    public CatalogoService(ProdottoRepository prodotti, MovimentoRepository movimenti, Clock clock) {
        this.prodotti = prodotti;
        this.movimenti = movimenti;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Prodotto> elenco() {
        return prodotti.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Prodotto trova(String codice) {
        return prodotti.findByCodice(codice)
                .orElseThrow(() -> new RisorsaNonTrovataException("Prodotto " + codice + " non trovato"));
    }

    /** Registra un nuovo prodotto, inizialmente senza giacenza. */
    public Prodotto crea(String codice, String nome, BigDecimal prezzo, int scortaMinima) {
        String codiceNormalizzato = codice == null ? null : codice.strip();
        if (codiceNormalizzato != null && prodotti.existsByCodice(codiceNormalizzato)) {
            throw new RichiestaNonValidaException(
                    "Esiste già un prodotto con codice " + codiceNormalizzato);
        }
        String nomeNormalizzato = nome == null ? null : nome.strip();
        return prodotti.save(new Prodotto(codiceNormalizzato, nomeNormalizzato,
                prezzo, 0, scortaMinima));
    }

    /** R2-R4: la richiesta è già validata da Riassortimento; qui giacenza e movimento insieme. */
    public Prodotto riassortisci(String codice, Riassortimento richiesta) {
        Prodotto prodotto = trova(codice);
        prodotto.riassortisci(richiesta.quantita());
        movimenti.save(MovimentoMagazzino.carico(prodotto, richiesta, LocalDateTime.now(clock)));
        return prodotto;
    }

    /** Registra lo smaltimento delle scorte insieme al relativo movimento. */
    public Prodotto smaltisci(String codice, Smaltimento richiesta) {
        Prodotto prodotto = trova(codice);
        prodotto.smaltisci(richiesta.quantita());
        movimenti.save(MovimentoMagazzino.smaltimento(
                prodotto, richiesta, LocalDateTime.now(clock)));
        return prodotto;
    }

    /** R6: lo storico, dal movimento più recente. */
    @Transactional(readOnly = true)
    public List<MovimentoMagazzino> movimenti() {
        return movimenti.findAllByOrderByCreatoIlDescIdDesc();
    }
}
