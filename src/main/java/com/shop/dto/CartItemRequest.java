package com.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartItemRequest {

    @NotNull(message = "Product ID is required")
    @Schema(example = "1")
    private Long productId;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Total quantity to add — incremented onto any quantity already in the cart for this product", example = "1")
    private int quantity;
}