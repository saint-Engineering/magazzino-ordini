package com.epicode.magazzino.model;

/** Una richiesta di rimozione di scorte da smaltire. */
public record Smaltimento(int quantita, String nota) {

    public Smaltimento {
        if (quantita < 1) {
            throw new RichiestaNonValidaException(
                    "La quantità da smaltire deve essere maggiore di zero");
        }
        nota = (nota == null || nota.isBlank()) ? null : nota.strip();
        if (nota != null && nota.length() > Riassortimento.MASSIMO_NOTA) {
            throw new RichiestaNonValidaException(
                    "La nota può avere al massimo " + Riassortimento.MASSIMO_NOTA + " caratteri");
        }
    }
}
