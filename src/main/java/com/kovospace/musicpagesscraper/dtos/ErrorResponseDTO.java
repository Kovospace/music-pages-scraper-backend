package com.kovospace.musicpagesscraper.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ErrorResponse", description = "Body returned with every non-2xx response.")
public class ErrorResponseDTO {
  @Schema(description = "Human readable description of what went wrong.",
    example = "Platform of given name not found.")
  private String error;

  public ErrorResponseDTO(String error) {
    this.error = error;
  }

  public String getError() {
    return error;
  }
}
