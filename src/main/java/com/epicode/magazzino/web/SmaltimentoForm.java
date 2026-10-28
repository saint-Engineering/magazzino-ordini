package com.epicode.magazzino.web;

/** Dati del form per lo smaltimento di scorte. */
public class SmaltimentoForm {

    private Integer quantita;
    private String nota;

    public Integer getQuantita() {
        return quantita;
    }

    public void setQuantita(Integer quantita) {
        this.quantita = quantita;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }
}
