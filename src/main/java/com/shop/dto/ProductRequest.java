package com.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 1, max = 255)
    @Schema(description = "Must be unique across products", example = "iPhone 15 Pro")
    private String name;

    @Size(max = 1000)
    @Schema(description = "Free-text product description", example = "Titanium design, A17 Pro chip")
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    @Schema(description = "Unit price", example = "45000.0")
    private Double price;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    @Schema(description = "Units available", example = "10")
    private Integer stock;

    @NotBlank(message = "Category is required")
    @Size(max = 100)
    @Schema(description = "Used for filtering via GET /api/products/category/{category}", example = "Electronics")
    private String category;
}
