package com.kovospace.musicpagesscraper.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Bands", description = "One page of a bands listing.")
public  interface Bands
        extends PageableItem
{
    @Schema(description =
      "Bands found on this page. Every entry carries at least `platform`, `title`, `href` and " +
      "`slug`; `tracks` stays empty here. Platforms may add their own fields on top " +
      "(bandzone for instance adds `imageUrl`, `genre` and `city`).")
    List<?> getBands();
}
