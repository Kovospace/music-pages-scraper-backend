package com.kovospace.musicpagesscraper.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kovospace.musicpagesscraper.annotations.Compatibility;
import com.kovospace.musicpagesscraper.dtos.ErrorResponseDTO;
import com.kovospace.musicpagesscraper.exceptions.FactoryException;
import com.kovospace.musicpagesscraper.interfaces.Band;
import com.kovospace.musicpagesscraper.services.BandService;
import com.kovospace.musicpagesscraper.services.duration.TrackDurationService;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2 of the band profile: identical to v1 plus a {@code durationMs} on every track.
 *
 * <p>Only this one endpoint is re-published; everything else keeps being served by
 * {@link MainController} under /v1.
 */
@RestController
@RequestMapping("/v2")
@Tag(name = "Scraper v2", description = "Band profiles carrying track durations")
public class MainControllerV2 {

  private static final String TRACKS = "tracks";
  private static final String HREF_HASH = "hrefHash";
  private static final String HREF = "href";
  private static final String DURATION_MS = "durationMs";

  private final BandService bandService;
  private final TrackDurationService durationService;
  private final ObjectMapper objectMapper;

  @Autowired
  public MainControllerV2(
    BandService bandService,
    TrackDurationService durationService,
    ObjectMapper objectMapper
  ) {
    this.bandService = bandService;
    this.durationService = durationService;
    this.objectMapper = objectMapper;
  }

  @Operation(
    operationId = "getBandV2",
    summary = "Get a band profile with its tracks and their durations",
    description =
      "Same payload as `/v1/{s}/band` with one field added to every track: `durationMs`, the " +
      "playing time in milliseconds.\n\n" +
      "The scraped pages publish no track length anywhere, so it is read out of the audio itself " +
      "and kept forever. That cannot happen while a request is waiting, so a track whose duration " +
      "has not been computed yet answers `\"durationMs\": null` and is queued in the background — " +
      "repeat the request and it will be filled in.\n\n" +
      "Clients should treat `null` as *unknown*, not as zero, and fall back to whatever their " +
      "player reports."
  )
  @ApiResponses({
    @ApiResponse(responseCode = "200",
      description = "Band profile including tracks, each with `durationMs` (nullable)",
      content = @Content(schema = @Schema(implementation = Band.class))),
    @ApiResponse(responseCode = "400", description = "Wrong type of an input parameter",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Unknown platform, or band not found",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "501", description = "Scraped page structure changed, data could not be extracted",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
    @ApiResponse(responseCode = "503", description = "No scraper service available for the platform",
      content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
  })
  @Compatibility(
    since = "v2",
    breaking = false,
    supersedes = "/v1/{s}/band",
    changes = {"added nullable field `durationMs` to every track"},
    notes = "Additive only. Clients that ignore unknown fields can keep using /v1 unchanged."
  )
  @GetMapping(value = "/{s}/band", produces = "application/json;charset=utf-8")
  public JsonNode getBand(
    @Parameter(description = "Band slug, taken from a bands listing.", required = true,
      example = "sifon")
    @RequestParam String q,
    @Parameter(description = "Platform slug, as returned by /platforms.", required = true,
      example = "bandzone")
    @PathVariable String s
  ) throws FactoryException {
    Band band = bandService.getBand(q, s);

    // Serialising through the same ObjectMapper the message converter uses keeps the payload
    // byte-identical to v1, whether the band came from the cache as a DTO or straight off the
    // scraper - only then is durationMs added on top.
    ObjectNode tree = objectMapper.valueToTree(band);
    JsonNode tracks = tree.get(TRACKS);
    if (tracks == null || !tracks.isArray()) {
      return tree;
    }

    Set<String> hashes = new LinkedHashSet<>();
    for (JsonNode track : tracks) {
      String hash = text(track, HREF_HASH);
      if (hash != null) {
        hashes.add(hash);
      }
    }

    Map<String, Long> known = durationService.known(hashes);
    List<TrackDurationService.TrackRef> missing = new ArrayList<>();

    for (JsonNode track : tracks) {
      String hash = text(track, HREF_HASH);
      Long durationMs = hash == null ? null : known.get(hash);
      ((ObjectNode) track).put(DURATION_MS, durationMs);
      if (hash != null && durationMs == null) {
        missing.add(new TrackDurationService.TrackRef(hash, text(track, HREF)));
      }
    }

    durationService.requestMissing(missing);
    return tree;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}
