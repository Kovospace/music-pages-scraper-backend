package com.kovospace.musicpagesscraper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

  @Value("${server.port:4000}")
  private int serverPort;

  @Bean
  public OpenAPI musicPagesScraperOpenAPI() {
    return new OpenAPI()
      .info(new Info()
        .title("MusicPagesScraper API")
        .version("v1")
        .description(
          "REST API for scraping bands and their track listings from music platforms.\n\n" +
          "Results are cached in the database for a given time, so repeated queries are served " +
          "without hitting the scraped site again.\n\n" +
          "Every endpoint is a plain `GET` and returns `application/json;charset=utf-8`.\n\n" +
          "**Versioning** — the version is the first path segment (`/v1/...`). A breaking change " +
          "to an endpoint is published as a new version of *that endpoint only*; everything else " +
          "keeps being served from the version it already has.")
        .license(new License().name("MIT")))
      .externalDocs(new ExternalDocumentation()
        .description("Project README on GitHub")
        .url("https://github.com/Kovospace/music-pages-scraper-backend"))
      .servers(Arrays.asList(
        new Server()
          .url("https://music-pages-scraper.matejkovac.sk")
          .description("Live"),
        new Server()
          .url("http://localhost:" + serverPort)
          .description("Local development")));
  }

  // One group per API version -> version dropdown in Swagger UI, one spec per version for clients.
  // Adding a version means adding a bean here and a controller with the matching @RequestMapping.
  //
  // NOTE: declaring groups moves the spec off /v3/api-docs; each group is served from
  // /v3/api-docs/{group}. /v3/api-docs itself then lists the available groups.

  @Bean
  public GroupedOpenApi v1Api() {
    return GroupedOpenApi.builder()
      .group("v1")
      .pathsToMatch("/v1/**")
      .build();
  }

  @Bean
  public GroupedOpenApi v2Api() {
    return GroupedOpenApi.builder()
      .group("v2")
      .pathsToMatch("/v2/**")
      .build();
  }
}
