package com.kovospace.musicpagesscraper.controllers;

import com.kovospace.musicpagesscraper.annotations.Compatibility;
import com.kovospace.musicpagesscraper.dtos.ErrorResponseDTO;
import com.kovospace.musicpagesscraper.exceptions.FactoryException;
import com.kovospace.musicpagesscraper.exceptions.PageException;
import com.kovospace.musicpagesscraper.interfaces.Band;
import com.kovospace.musicpagesscraper.interfaces.Bands;
import com.kovospace.musicpagesscraper.interfaces.Platforms;
import com.kovospace.musicpagesscraper.services.BandService;
import com.kovospace.musicpagesscraper.services.BandsService;
import com.kovospace.musicpagesscraper.services.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
// API version lives in the first path segment, so every endpoint of v1 is reachable under /v1/**.
// A future v2 gets its own controller with @RequestMapping("/v2") and can re-implement only the
// endpoints that actually break; the rest keep being served from here.
@RequestMapping("/v1")
@Tag(name = "Scraper v1", description = "Querying bands, band profiles and supported platforms")
public class MainController {

  private BandsService bandsService;
  private BandService bandService;
  private PlatformService platformService;

  @Autowired
  public MainController(
    BandsService bandsService,
    BandService bandService,
    PlatformService platformService
  ) {
    this.bandsService = bandsService;
    this.bandService = bandService;
    this.platformService = platformService;
  }

  @Operation(
    operationId = "getBandsFromPlatform",
    summary = "List bands from a single platform",
    description =
      "Searches one platform for bands matching the query. With no query the platform's own " +
      "default listing (newest, most popular, ... depending on the site) is returned."
  )
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paged list of bands"),
    @ApiResponse(responseCode = "400", description = "Wrong type of an input parameter",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Unknown platform, or scraped page not found",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "501", description = "Scraped page structure changed, data could not be extracted",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "503", description = "No scraper service available for the platform",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @Compatibility(since = "v1")
  @GetMapping(value = "/{s}/bands", produces = "application/json;charset=utf-8")
  public Bands getBands(
    @Parameter(description = "Band name to search for. Empty means the platform's default listing.",
      example = "Sifon")
    @RequestParam(defaultValue = "") String q,
    @Parameter(description = "Page number, starting at 1.", example = "1")
    @RequestParam(defaultValue = "1") int p,
    @Parameter(description = "Platform slug, as returned by /platforms.", required = true,
      example = "bandzone")
    @PathVariable String s
  )
  throws PageException, FactoryException {
    return bandsService.getBands(q, p, s);
  }

  @Operation(
    operationId = "getBandsFromAllPlatforms",
    summary = "List bands across platforms",
    description =
      "Searches several platforms at once and merges the results into one paged list. " +
      "Repeat the `s` parameter to pick platforms (`?s=bandzone&s=freeteknomusic`); " +
      "omit it to search every supported platform.\n\n" +
      "Errors of individual platforms are swallowed here — a platform that fails simply " +
      "contributes no bands, so this endpoint answers `200` as long as the request itself is valid."
  )
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Merged paged list of bands"),
    @ApiResponse(responseCode = "400", description = "Wrong type of an input parameter",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @Compatibility(since = "v1")
  @GetMapping(value = "/bands", produces = "application/json;charset=utf-8")
  // foo=value1&foo=value2&foo=value3
  public Bands getBands(
    @Parameter(description = "Band name to search for. Empty means each platform's default listing.",
      example = "Sifon")
    @RequestParam(defaultValue = "") String q,
    @Parameter(description = "Page number, starting at 1.", example = "1")
    @RequestParam(defaultValue = "1") int p,
    @Parameter(description = "Platform slugs to scrape. Omit to scrape all supported platforms.",
      example = "bandzone")
    @RequestParam(required = false) Optional<List<String>> s // which pages to scrape none = all supported
  ) {
    return bandsService.getBands(q, p, s);
  }

  /*
  q - query - band name or id
  s - platform
  */
  @Operation(
    operationId = "getBand",
    summary = "Get a band profile with its tracks",
    description = "Returns one band together with its track listing, identified by the `slug` " +
      "obtained from any of the bands endpoints."
  )
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Band profile including tracks"),
    @ApiResponse(responseCode = "400", description = "Wrong type of an input parameter",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Unknown platform, or band not found",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "501", description = "Scraped page structure changed, data could not be extracted",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "503", description = "No scraper service available for the platform",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @Compatibility(since = "v1")
  @GetMapping(value = "/{s}/band", produces = "application/json;charset=utf-8")
  public Band getBand(
    @Parameter(description = "Band slug, taken from a bands listing.", required = true,
      example = "sifon")
    @RequestParam String q,
    @Parameter(description = "Platform slug, as returned by /platforms.", required = true,
      example = "bandzone")
    @PathVariable String s
  ) throws FactoryException {
      return bandService.getBand(q, s);
  }

  @Operation(
    operationId = "getPlatforms",
    summary = "List supported platforms",
    description = "Returns every platform the API can scrape, with the slug to use in the other endpoints."
  )
  @ApiResponse(responseCode = "200", description = "List of supported platforms")
  @Compatibility(since = "v1")
  @GetMapping(value = "/platforms", produces = "application/json;charset=utf-8")
  public Platforms getPlatforms(
    @Parameter(description = "Unused, kept for signature compatibility with the paged endpoints.",
      hidden = true)
    @RequestParam(defaultValue = "1", required = false) int p
  ) {
    return platformService.getAll();
  }

}
