package com.kovospace.musicpagesscraper.interfaces;

import com.kovospace.musicpagesscraper.repositories.platform.PlatformResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Platforms", description = "All platforms this API can scrape.")
public  interface Platforms
        extends PageableItem
{
    @Schema(description = "Supported platforms.")
    List<PlatformResponseDTO> getPlatforms();
}
