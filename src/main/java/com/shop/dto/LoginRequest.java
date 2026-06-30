package com.shop.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login only needs username/password — kept separate from AuthRequest (used for
 * register) so login isn't forced to require an email it never sends.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 255)
    @Schema(example = "jane.doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 255)
    @Schema(example = "correct-horse-battery-staple")
    private String password;
}
