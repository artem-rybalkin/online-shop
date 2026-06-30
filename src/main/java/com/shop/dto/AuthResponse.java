package com.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(example = "jane.doe") String username,
        @Schema(example = "jane.doe@example.com") String email) {
}
