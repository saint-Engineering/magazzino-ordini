package com.epicode.magazzino.config;

import com.epicode.magazzino.mcp.MagazzinoTools;
import com.epicode.magazzino.mcp.AmministrazioneTools;
import com.epicode.magazzino.mcp.PromptMagazzino;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.util.List;
import java.util.Set;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Tre server con trasporti, sessioni e cataloghi separati per ruolo. */
@Configuration(proxyBeanMethods = false)
public class McpServerConfig {

  @Bean
  McpJsonMapper serializzatoreMcp(JsonMapper mapper) {
    return new JacksonMcpJsonMapper(
        mapper.rebuild().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultPropertyInclusion(
                inclusione -> JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build());
  }

  @Bean
  WebMvcStreamableServerTransportProvider trasportoUser(McpJsonMapper mapper) {
    return trasporto("/mcp/user", mapper);
  }

  @Bean
  WebMvcStreamableServerTransportProvider trasportoMagazzino(
      McpJsonMapper mapper) {
    return trasporto("/mcp/magazzino", mapper);
  }

  @Bean
  WebMvcStreamableServerTransportProvider trasportoAdmin(McpJsonMapper mapper) {
    return trasporto("/mcp/admin", mapper);
  }

  @Bean(destroyMethod = "closeGracefully")
  McpSyncServer serverUser(
      @Qualifier("trasportoUser") WebMvcStreamableServerTransportProvider trasporto,
      McpJsonMapper mapper, MagazzinoTools tool, PromptMagazzino prompt) {
    return serverOperativo("USER", trasporto, mapper, tool, prompt,
        Set.of("cerca_prodotti", "prodotti_sotto_scorta", "dettaglio_ordine"),
        "Catalogo, scorte e ordini: usa esclusivamente i tool di lettura.");
  }

  @Bean(destroyMethod = "closeGracefully")
  McpSyncServer serverMagazzino(
      @Qualifier("trasportoMagazzino") WebMvcStreamableServerTransportProvider trasporto,
      McpJsonMapper mapper, MagazzinoTools tool, PromptMagazzino prompt) {
    return serverOperativo("MAGAZZINO", trasporto, mapper, tool, prompt,
        Set.of("cerca_prodotti", "prodotti_sotto_scorta",
            "riassortisci_prodotto", "crea_prodotto", "smaltisci_prodotto",
            "storico_movimenti"),
        "Catalogo, scorte e movimenti: crea_prodotto, riassortisci_prodotto "
            + "e smaltisci_prodotto modificano i dati e vanno usati solo "
            + "su richiesta esplicita dell'utente. Lo smaltimento rimuove scorte.");
  }

  @Bean(destroyMethod = "closeGracefully")
  McpSyncServer serverAdmin(
      @Qualifier("trasportoAdmin") WebMvcStreamableServerTransportProvider trasporto,
      McpJsonMapper mapper, AmministrazioneTools tool) {
    return McpServer.sync(trasporto).jsonMapper(mapper)
        .serverInfo("ADMIN", "0.3.0")
        .capabilities(ServerCapabilities.builder().tools(false).build())
        .tools(SyncMcpAnnotationProviders.toolSpecifications(List.of(tool)))
        .instructions(
            "Amministrazione: utenti_bloccati legge gli account bloccati "
                + "senza modificarli. Nessun prompt o tool operativo di magazzino.")
        .immediateExecution(true).build();
  }

  @Bean
  RouterFunction<ServerResponse> rotteMcp(
      @Qualifier("trasportoUser") WebMvcStreamableServerTransportProvider user,
      @Qualifier("trasportoMagazzino") WebMvcStreamableServerTransportProvider magazzino,
      @Qualifier("trasportoAdmin") WebMvcStreamableServerTransportProvider admin) {
    return user.getRouterFunction().and(magazzino.getRouterFunction())
        .and(admin.getRouterFunction());
  }

  private WebMvcStreamableServerTransportProvider trasporto(String endpoint,
      McpJsonMapper mapper) {
    return WebMvcStreamableServerTransportProvider.builder().jsonMapper(mapper)
        .mcpEndpoint(endpoint).build();
  }

  private McpSyncServer serverOperativo(String nome,
      WebMvcStreamableServerTransportProvider trasporto, McpJsonMapper mapper,
      MagazzinoTools tool, PromptMagazzino prompt, Set<String> consentiti,
      String istruzioni) {
    var specifiche = SyncMcpAnnotationProviders
        .toolSpecifications(List.of(tool)).stream()
        .filter(specifica -> consentiti.contains(specifica.tool().name()))
        .toList();
    return McpServer.sync(trasporto).jsonMapper(mapper)
        .serverInfo(nome, "0.3.0")
        .capabilities(
            ServerCapabilities.builder().tools(false).prompts(false).build())
        .tools(specifiche)
        .prompts(
            SyncMcpAnnotationProviders.promptSpecifications(List.of(prompt)))
        .instructions(istruzioni)
        // Mantiene il SecurityContext sul thread servlet per @PreAuthorize.
        .immediateExecution(true).build();
  }
}
