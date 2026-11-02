package com.epicode.magazzino.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.model.Ordine;
import com.epicode.magazzino.repository.OrdineRepository;
import com.epicode.magazzino.repository.ProdottoRepository;
import com.epicode.magazzino.repository.UtenteRepository;
import com.epicode.magazzino.service.CatalogoService;
import com.epicode.magazzino.service.UtenteService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Il server MCP provato come lo usa un agente: un client MCP vero, via HTTP,
 * sull'applicazione avviata su una porta libera.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MagazzinoMcpServerTest {

  private static final String CODICE = "T-MCP-1";

  @LocalServerPort
  private int porta;

  @Autowired
  private ProdottoRepository prodotti;

  @Autowired
  private OrdineRepository ordiniRepository;

  @Autowired
  private CatalogoService catalogo;

  @Autowired
  private UtenteRepository utentiRepository;

  @Autowired
  private UtenteService utenti;

  private McpSyncClient client;
  private String chiaveUtente;
  private String chiaveMagazziniere;
  private String chiaveAmministratore;

  @BeforeEach
  void collega() {
    if (prodotti.findByCodice(CODICE).isEmpty()) {
      prodotti.save(new Prodotto(CODICE, "Prodotto per il test MCP",
          new BigDecimal("2.00"), 3, 500));
    }
    chiaveUtente = utenti.generaChiaveMcp(
        utentiRepository.findByUsername("user").orElseThrow().getId());
    chiaveMagazziniere = utenti.generaChiaveMcp(
        utentiRepository.findByUsername("magazzinga").orElseThrow().getId());
    chiaveAmministratore = utenti.generaChiaveMcp(
        utentiRepository.findByUsername("admin").orElseThrow().getId());
    collegaCome(chiaveUtente);
  }

  private void collegaCome(String chiave) {
    String endpoint = chiave.equals(chiaveMagazziniere)
        ? "/mcp/magazzino"
        : chiave.equals(chiaveAmministratore) ? "/mcp/admin" : "/mcp/user";
    if (client != null) {
      client.closeGracefully();
    }
    client = McpClient
        .sync(HttpClientStreamableHttpTransport
            .builder("http://localhost:" + porta).endpoint(endpoint)
            .httpRequestCustomizer(
                (richiesta, metodo, uri, corpo, contesto) -> richiesta
                    .header("Authorization", "Bearer " + chiave))
            .build())
        .build();
    client.initialize();
  }

  @AfterEach
  void chiudi() {
    client.closeGracefully();
  }

  private CallToolResult chiama(String tool, Map<String, Object> argomenti) {
    return client
        .callTool(CallToolRequest.builder(tool).arguments(argomenti).build());
  }

  private String testo(CallToolResult risultato) {
    return risultato.content().stream().filter(TextContent.class::isInstance)
        .map(c -> ((TextContent) c).text()).collect(Collectors.joining());
  }

  @Test
  void userEsponeSoloTreToolDiLettura() {
    Map<String, Tool> tool = client.listTools().tools().stream()
        .collect(Collectors.toMap(Tool::name, Function.identity()));

    assertEquals(
        Set.of("cerca_prodotti", "prodotti_sotto_scorta", "dettaglio_ordine"),
        tool.keySet());
    assertEquals("USER", client.getServerInfo().name());
    assertTrue(tool.get("cerca_prodotti").annotations().readOnlyHint());
    assertTrue(tool.get("prodotti_sotto_scorta").annotations().readOnlyHint());
    assertTrue(tool.get("dettaglio_ordine").annotations().readOnlyHint());
  }

  @Test
  void magazzinoEsponeIlRiassortimentoMaNonGliOrdini() {
    collegaCome(chiaveMagazziniere);
    Map<String, Tool> tool = client.listTools().tools().stream()
        .collect(Collectors.toMap(Tool::name, Function.identity()));

    assertEquals(Set.of("cerca_prodotti", "prodotti_sotto_scorta",
        "riassortisci_prodotto"), tool.keySet());
    assertEquals("MAGAZZINO", client.getServerInfo().name());
    assertTrue(tool.get("cerca_prodotti").annotations().readOnlyHint());
    assertTrue(tool.get("prodotti_sotto_scorta").annotations().readOnlyHint());
    assertFalse(tool.get("riassortisci_prodotto").annotations().readOnlyHint());
    assertFalse(
        tool.get("riassortisci_prodotto").annotations().destructiveHint());
    assertEquals(List.of("piano-riordino"),
        client.listPrompts().prompts().stream().map(Prompt::name).toList());
  }

  @Test
  @SuppressWarnings("unchecked")
  void laRicercaRestituisceDatiStrutturati() {
    CallToolResult risultato = chiama("cerca_prodotti",
        Map.of("testo", "t-mcp"));

    assertFalse(Boolean.TRUE.equals(risultato.isError()));
    Map<String, Object> dati = (Map<String, Object>) risultato
        .structuredContent();
    List<Map<String, Object>> trovati = (List<Map<String, Object>>) dati
        .get("prodotti");
    assertEquals(CODICE, trovati.get(0).get("codice"));
    assertEquals(Boolean.TRUE, trovati.get(0).get("daRiassortire"));
  }

  @Test
  void unOrdineCheNonEsisteEUnErroreCheIlModelloPuoLeggere() {
    CallToolResult risultato = chiama("dettaglio_ordine",
        Map.of("numero", 999999));

    assertTrue(risultato.isError());
    assertTrue(testo(risultato).contains("Ordine 999999 non trovato"));
  }

  @Test
  void unaQuantitaFuoriLimiteNonCambiaLaGiacenza() {
    collegaCome(chiaveMagazziniere);
    int prima = catalogo.trova(CODICE).getDisponibili();

    CallToolResult risultato = chiama("riassortisci_prodotto",
        Map.of("codice", CODICE, "quantita", 1001));

    assertTrue(risultato.isError());
    assertTrue(testo(risultato).contains("va da 1 a 1000"));
    assertEquals(prima, catalogo.trova(CODICE).getDisponibili());
  }

  @Test
  void ilRiassortimentoAumentaLaGiacenza() {
    collegaCome(chiaveMagazziniere);
    int prima = catalogo.trova(CODICE).getDisponibili();

    CallToolResult risultato = chiama("riassortisci_prodotto",
        Map.of("codice", CODICE, "quantita", 4, "nota", "Test MCP"));

    assertFalse(Boolean.TRUE.equals(risultato.isError()));
    assertEquals(prima + 4, catalogo.trova(CODICE).getDisponibili());
  }

  @Test
  void utenteNonPuoRiassortireTramiteMcp() {
    int prima = catalogo.trova(CODICE).getDisponibili();

    assertThrows(McpError.class, () -> chiama("riassortisci_prodotto",
        Map.of("codice", CODICE, "quantita", 4)));
    assertEquals(prima, catalogo.trova(CODICE).getDisponibili());
  }

  @Test
  void utenteLeggeIlDettaglioOrdineConMcp() {
    Ordine ordine = ordiniRepository
        .save(new Ordine("Cliente MCP", LocalDateTime.of(2026, 11, 2, 10, 0)));

    try {
      CallToolResult risultato = chiama("dettaglio_ordine",
          Map.of("numero", ordine.getId()));

      assertFalse(Boolean.TRUE.equals(risultato.isError()));
      assertTrue(testo(risultato).contains("Cliente MCP"));
    } finally {
      ordiniRepository.deleteById(ordine.getId());
    }
  }

  @Test
  void magazziniereNonAccedeAiDettagliOrdineMcp() {
    Ordine ordine = ordiniRepository
        .save(new Ordine("Cliente MCP", LocalDateTime.of(2026, 11, 2, 10, 0)));

    try {
      collegaCome(chiaveMagazziniere);

      assertThrows(McpError.class,
          () -> chiama("dettaglio_ordine", Map.of("numero", ordine.getId())));
    } finally {
      ordiniRepository.deleteById(ordine.getId());
    }
  }

  @Test
  void adminSiInizializzaSenzaToolNePrompt() {
    collegaCome(chiaveAmministratore);

    assertEquals("ADMIN", client.getServerInfo().name());
    assertNull(client.getServerCapabilities().tools());
    assertNull(client.getServerCapabilities().prompts());
    assertThrows(IllegalStateException.class,
        () -> chiama("cerca_prodotti", Map.of("testo", "T-MCP")));
    assertThrows(McpError.class, () -> client
        .getPrompt(GetPromptRequest.builder("piano-riordino").build()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tools/list", "tools/call", "prompts/list",
      "prompts/get"})
  void adminNonRegistraMetodiPerToolOPrompt(String metodo) throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      HttpRequest inizializzazione = richiestaMcp("admin", chiaveAmministratore)
          .POST(HttpRequest.BodyPublishers.ofString("""
              {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                "protocolVersion":"2025-03-26","capabilities":{},
                "clientInfo":{"name":"test-admin","version":"1.0"}}}
              """)).build();
      HttpResponse<String> risposta = http.send(inizializzazione,
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, risposta.statusCode());
      String sessione = risposta.headers().firstValue("Mcp-Session-Id")
          .orElseThrow();
      try {
        HttpRequest richiesta = richiestaMcp("admin", chiaveAmministratore)
            .header("Mcp-Session-Id", sessione)
            .header("MCP-Protocol-Version", "2025-03-26")
            .POST(HttpRequest.BodyPublishers.ofString("""
                {"jsonrpc":"2.0","id":2,"method":"%s","params":{}}
                """.formatted(metodo))).build();
        HttpResponse<String> errore = http.send(richiesta,
            HttpResponse.BodyHandlers.ofString());

        assertEquals(200, errore.statusCode());
        assertTrue(errore.body().contains("\"code\":-32601"), errore.body());
      } finally {
        http.send(
            richiestaMcp("admin", chiaveAmministratore)
                .header("Mcp-Session-Id", sessione).DELETE().build(),
            HttpResponse.BodyHandlers.discarding());
      }
    }
  }

  private HttpRequest.Builder richiestaMcp(String endpoint, String chiave) {
    return HttpRequest
        .newBuilder(
            URI.create("http://localhost:" + porta + "/mcp/" + endpoint))
        .header("Authorization", "Bearer " + chiave)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream");
  }

  @ParameterizedTest
  @CsvSource({"user, MAGAZZINO", "user, ADMIN", "magazzino, USER",
      "magazzino, ADMIN", "admin, USER", "admin, MAGAZZINO"})
  void ogniEndpointRifiutaLeChiaviDegliAltriRuoli(String endpoint, String ruolo)
      throws Exception {
    String chiave = switch (ruolo) {
      case "USER" -> chiaveUtente;
      case "MAGAZZINO" -> chiaveMagazziniere;
      case "ADMIN" -> chiaveAmministratore;
      default -> throw new IllegalArgumentException(ruolo);
    };
    HttpRequest richiesta = richiestaMcp(endpoint, chiave)
        .POST(HttpRequest.BodyPublishers.ofString("{}")).build();

    try (HttpClient http = HttpClient.newHttpClient()) {
      assertEquals(403, http
          .send(richiesta, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
  }

  @Test
  void unaSessioneUserNonEValidaSulServerMagazzino() throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      HttpRequest inizializzazione = richiestaMcp("user", chiaveUtente)
          .POST(HttpRequest.BodyPublishers.ofString("""
              {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                "protocolVersion":"2025-03-26","capabilities":{},
                "clientInfo":{"name":"test-isolamento","version":"1.0"}}}
              """)).build();
      HttpResponse<String> risposta = http.send(inizializzazione,
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, risposta.statusCode());
      String sessione = risposta.headers().firstValue("Mcp-Session-Id")
          .orElseThrow();
      HttpRequest richiesta = richiestaMcp("magazzino", chiaveMagazziniere)
          .header("Mcp-Session-Id", sessione)
          .header("MCP-Protocol-Version", "2025-03-26")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
          .build();

      assertEquals(404, http
          .send(richiesta, HttpResponse.BodyHandlers.ofString()).statusCode());
      http.send(
          HttpRequest.newBuilder(inizializzazione.uri())
              .header("Authorization", "Bearer " + chiaveUtente)
              .header("Mcp-Session-Id", sessione).DELETE().build(),
          HttpResponse.BodyHandlers.discarding());
    }
  }

  @Test
  void ilPianoDiRiordinoEsponeLaCoperturaFacoltativa() {
    List<Prompt> disponibili = client.listPrompts().prompts();

    assertEquals(1, disponibili.size());
    Prompt piano = disponibili.get(0);
    assertEquals("piano-riordino", piano.name());
    assertEquals("Piano di riordino", piano.title());
    assertEquals(1, piano.arguments().size());
    assertEquals("copertura", piano.arguments().get(0).name());
    assertFalse(Boolean.TRUE.equals(piano.arguments().get(0).required()));
  }

  @Test
  void ilPromptSenzaCoperturaUsaDueVolteLaScortaMinima() {
    int prima = catalogo.trova(CODICE).getDisponibili();

    GetPromptResult risultato = client
        .getPrompt(GetPromptRequest.builder("piano-riordino").build());

    assertEquals("Piano di riordino", risultato.description());
    assertEquals(1, risultato.messages().size());
    assertEquals(Role.USER, risultato.messages().get(0).role());
    TextContent contenuto = assertInstanceOf(TextContent.class,
        risultato.messages().get(0).content());
    assertTrue(contenuto.text().contains("2 volte"));
    assertEquals(prima, catalogo.trova(CODICE).getDisponibili());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 6})
  void ilPromptRifiutaLaCoperturaFuoriLimiteSenzaCambiareLaGiacenza(
      int copertura) {
    int prima = catalogo.trova(CODICE).getDisponibili();

    McpError errore = assertThrows(McpError.class,
        () -> client.getPrompt(GetPromptRequest.builder("piano-riordino")
            .arguments(Map.of("copertura", copertura)).build()));

    assertTrue(errore.getMessage().contains("La copertura va da 1 a 5"));
    assertEquals(prima, catalogo.trova(CODICE).getDisponibili());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 5})
  void ilPromptDelPianoUsaSoloLaLettura(int copertura) {
    int prima = catalogo.trova(CODICE).getDisponibili();
    String messaggio = client
        .getPrompt(GetPromptRequest.builder("piano-riordino")
            .arguments(Map.of("copertura", copertura)).build())
        .messages().stream().map(m -> ((TextContent) m.content()).text())
        .collect(Collectors.joining());

    assertTrue(messaggio.contains("prodotti_sotto_scorta"));
    assertTrue(messaggio.contains(copertura + " volte"));
    assertTrue(messaggio.contains("Non chiamare riassortisci_prodotto"));
    assertEquals(prima, catalogo.trova(CODICE).getDisponibili());
  }
}
