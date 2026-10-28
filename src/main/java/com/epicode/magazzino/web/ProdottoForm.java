package com.epicode.magazzino.web;

import java.math.BigDecimal;

/** Dati del form per censire un prodotto. */
public class ProdottoForm {

    private String codice;
    private String nome;
    private BigDecimal prezzo;
    private Integer scortaMinima;

    public String getCodice() {
        return codice;
    }

    public void setCodice(String codice) {
        this.codice = codice;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getPrezzo() {
        return prezzo;
    }

    public void setPrezzo(BigDecimal prezzo) {
        this.prezzo = prezzo;
    }

    public Integer getScortaMinima() {
        return scortaMinima;
    }

    public void setScortaMinima(Integer scortaMinima) {
        this.scortaMinima = scortaMinima;
    }
}
