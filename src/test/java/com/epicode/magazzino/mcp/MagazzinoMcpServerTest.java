package com.epicode.magazzino.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicode.magazzino.model.Prodotto;
import com.epicode.magazzino.repository.ProdottoRepository;
import com.epicode.magazzino.service.CatalogoService;
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
  private CatalogoService catalogo;

  private McpSyncClient client;

  @BeforeEach
  void collega() {
    if (prodotti.findByCodice(CODICE).isEmpty()) {
      prodotti.save(new Prodotto(CODICE, "Prodotto per il test MCP",
          new BigDecimal("2.00"), 3, 500));
    }
    client = McpClient.sync(HttpClientStreamableHttpTransport
        .builder("http://localhost:" + porta).build()).build();
    client.initialize();
  }

  @AfterEach
  void chiudi() {
    client.closeGracefully();
  }

  private CallToolResult chiama(String tool, Map<String, Object> argomenti) {
    return client.callTool(
        CallToolRequest.builder(tool).arguments(argomenti).build());
  }

  private String testo(CallToolResult risultato) {
    return risultato.content().stream().filter(TextContent.class::isInstance)
        .map(c -> ((TextContent) c).text()).collect(Collectors.joining());
  }

  @Test
  void quattroToolESoloQuelloCheScriveNonESolaLettura() {
    Map<String, Tool> tool = client.listTools().tools().stream()
        .collect(Collectors.toMap(Tool::name, Function.identity()));

    assertEquals(Set.of("cerca_prodotti", "prodotti_sotto_scorta",
        "dettaglio_ordine", "riassortisci_prodotto"), tool.keySet());
    assertTrue(tool.get("cerca_prodotti").annotations().readOnlyHint());
    assertTrue(tool.get("dettaglio_ordine").annotations().readOnlyHint());
    assertFalse(tool.get("riassortisci_prodotto").annotations().readOnlyHint());
    assertFalse(
        tool.get("riassortisci_prodotto").annotations().destructiveHint());
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
    int prima = catalogo.trova(CODICE).getDisponibili();

    CallToolResult risultato = chiama("riassortisci_prodotto",
        Map.of("codice", CODICE, "quantita", 1001));

    assertTrue(risultato.isError());
    assertTrue(testo(risultato).contains("va da 1 a 1000"));
    assertEquals(prima, catalogo.trova(CODICE).getDisponibili());
  }

  @Test
  void ilRiassortimentoAumentaLaGiacenza() {
    int prima = catalogo.trova(CODICE).getDisponibili();

    CallToolResult risultato = chiama("riassortisci_prodotto",
        Map.of("codice", CODICE, "quantita", 4, "nota", "Test MCP"));

    assertFalse(Boolean.TRUE.equals(risultato.isError()));
    assertEquals(prima + 4, catalogo.trova(CODICE).getDisponibili());
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

    GetPromptResult risultato = client.getPrompt(
        GetPromptRequest.builder("piano-riordino").build());

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
