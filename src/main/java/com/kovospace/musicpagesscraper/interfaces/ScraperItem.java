package com.kovospace.musicpagesscraper.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

public interface ScraperItem {
    @Schema(description = "Name of the band or track.", example = "Sifon")
    String getTitle();
}
