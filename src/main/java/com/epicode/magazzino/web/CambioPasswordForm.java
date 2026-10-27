package com.epicode.magazzino.web;

/** Credenziali richieste per la sostituzione della password corrente. */
public class CambioPasswordForm {

  private String passwordAttuale;
  private String nuovaPassword;
  private String confermaPassword;

  public String getPasswordAttuale() {
    return passwordAttuale;
  }

  public void setPasswordAttuale(String passwordAttuale) {
    this.passwordAttuale = passwordAttuale;
  }

  public String getNuovaPassword() {
    return nuovaPassword;
  }

  public void setNuovaPassword(String nuovaPassword) {
    this.nuovaPassword = nuovaPassword;
  }

  public String getConfermaPassword() {
    return confermaPassword;
  }

  public void setConfermaPassword(String confermaPassword) {
    this.confermaPassword = confermaPassword;
  }
}
