package com.kovospace.musicpagesscraper.repositories.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "Platform", description = "A platform that can be scraped.")
public class PlatformResponseDTO {
    @Schema(description = "Slug to pass as the `s` parameter of the other endpoints.",
      example = "bandzone")
    private String name;

    @Schema(description = "What kind of music the platform hosts.",
      example = "Contains mainly garage and underground bands from Czech Republic and Slovakia.")
    private String description;
}
