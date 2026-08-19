package com.kovospace.musicpagesscraper.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TrackDuration", description = "Playing time of a single track.")
public class TrackDurationDTO {

  @Schema(description = "Track download URL the duration was read from.",
    example = "https://bandzone.cz/track/download/208316")
  private final String href;

  @Schema(description = "MD5 of the href, the same key the band profile carries.",
    example = "6f1ed002ab5595859014ebf0951522d9")
  private final String hrefHash;

  @Schema(description =
    "Playing time in milliseconds, or null when the audio could not be read. Never zero.",
    example = "78602", nullable = true)
  private final Long durationMs;

  public TrackDurationDTO(String href, String hrefHash, Long durationMs) {
    this.href = href;
    this.hrefHash = hrefHash;
    this.durationMs = durationMs;
  }

  public String getHref() {
    return href;
  }

  public String getHrefHash() {
    return hrefHash;
  }

  public Long getDurationMs() {
    return durationMs;
  }
}
