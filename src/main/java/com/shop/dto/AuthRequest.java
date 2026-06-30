package com.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 255)
    @Schema(description = "Desired login username", example = "jane.doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(max = 255)
    @Schema(description = "Plaintext password — hashed with BCrypt before storage", example = "correct-horse-battery-staple")
    private String password;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(description = "Must be unique across accounts", example = "jane.doe@example.com")
    private String email;
}