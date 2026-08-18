package com.kovospace.musicpagesscraper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
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
        .version("2.0")
        .description(
          "REST API for scraping bands and their track listings from music platforms.\n\n" +
          "Results are cached in the database for a given time, so repeated queries are served " +
          "without hitting the scraped site again.\n\n" +
          "Every endpoint is a plain `GET` and returns `application/json;charset=utf-8`.")
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
}
