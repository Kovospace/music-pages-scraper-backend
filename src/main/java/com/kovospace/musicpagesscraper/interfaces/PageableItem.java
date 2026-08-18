package com.kovospace.musicpagesscraper.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

public interface PageableItem {
  @Schema(description = "Number of items listed in this page.", example = "20")
  int getCurrentPageItemsCount();

  @Schema(description = "Total number of pages. 1 even when nothing was found.", example = "5")
  int getPagesCount();

  @Schema(description = "Number of the returned page, starting at 1.", example = "1")
  int getCurrentPageNum();

  @Schema(description = "Number of items across all pages.", example = "97")
  int getTotalItemsCount();
}
