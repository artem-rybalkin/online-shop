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
public class OrderRequest {

    @NotBlank(message = "Session ID is required")
    @Size(max = 255)
    @Schema(description = "Identifies whose cart to check out — must match the sessionId used when adding items", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String sessionId;

    @NotBlank(message = "Customer name is required")
    @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @Schema(example = "Jane Doe")
    private String customerName;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255)
    @Schema(example = "jane.doe@example.com")
    private String customerEmail;
}
