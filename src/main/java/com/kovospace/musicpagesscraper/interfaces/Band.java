package com.kovospace.musicpagesscraper.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Band", description = "A band profile together with its tracks.")
public  interface Band
        extends ScraperItem
{
    @Schema(description = "Platform the band was scraped from.", example = "bandzone")
    String getPlatform();

    @Schema(description = "Unique identifier of the band within its platform.", example = "sifon")
    String getSlug();

    @Schema(description =
      "Tracks of the band, empty when it has none. Every entry carries at least `title`, " +
      "`slugRef`, `href` and `hrefHash`; platforms may add their own fields on top " +
      "(bandzone for instance adds `albumTitle`, `albumLabel` and `albumReleaseYear`).")
    List<?> getTracks();
}
