package com.epicode.magazzino.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica login, accesso anonimo e separazione dei permessi tra ruoli. */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticazioneTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void utentiNonAutenticatiSonoInviatiAlLogin() throws Exception {
        mvc.perform(get("/prodotti"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void utenteAccedeAllePagineApplicativeMaNonAllAmministrazione() throws Exception {
        var login = mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "user")
                        .param("password", "Uz3rUz3rUz3r"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/prodotti"))
                .andReturn();

        mvc.perform(get("/prodotti").session((org.springframework.mock.web.MockHttpSession) login.getRequest()
                        .getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trapano a batteria 18V")));
        mvc.perform(get("/utenti").session((org.springframework.mock.web.MockHttpSession) login.getRequest()
                        .getSession(false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void amministratoreAccedeSoloAllaGestioneUtenti() throws Exception {
        var login = mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "Nimd4Nimd4Nimda"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti"))
                .andReturn();
        var sessione = login.getRequest().getSession(false);

        mvc.perform(get("/utenti").session((org.springframework.mock.web.MockHttpSession) sessione))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("admin")));
        mvc.perform(get("/utenti/nuovo").session((org.springframework.mock.web.MockHttpSession) sessione))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aggiungi utente")));
        mvc.perform(get("/prodotti").session((org.springframework.mock.web.MockHttpSession) sessione))
                .andExpect(status().isForbidden());
    }

    @Test
    void credenzialiErrateMostranoMessaggioNonRivelatore() throws Exception {
        mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "user")
                        .param("password", "password errata"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?errore"));

        mvc.perform(get("/login?errore"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Autenticazione negata")));
    }
}
