package com.epicode.magazzino.model;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Account applicativo persistito con password BCrypt e ruolo numerico. */
@Entity
@Table(name = "UTENTI")
public class Utente implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 100)
    private String cognome;

    @Column(nullable = false, columnDefinition = "integer not null check (ruolo in (0, 1))")
    private int ruolo;

    @Column(nullable = false)
    private int errori;

    protected Utente() {}

    public Utente(String username, String passwordHash, String nome, String cognome, int ruolo) {
        aggiornaDati(username, nome, cognome, ruolo);
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    public String getNome() {
        return nome;
    }

    public String getCognome() {
        return cognome;
    }

    public int getRuolo() {
        return ruolo;
    }

    public int getErrori() {
        return errori;
    }

    public void aggiornaDati(String username, String nome, String cognome, int ruolo) {
        RuoloUtente.daValore(ruolo);
        this.username = username;
        this.nome = nome;
        this.cognome = cognome;
        this.ruolo = ruolo;
    }

    public void aggiornaPassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void incrementaErrori() {
        errori++;
    }

    public void azzeraErrori() {
        errori = 0;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = ruolo == 0 ? "ROLE_AMMINISTRATORE" : "ROLE_UTENTE";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return errori < 3;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
