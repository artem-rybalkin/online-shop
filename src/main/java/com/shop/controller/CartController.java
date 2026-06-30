package com.shop.controller;

import com.shop.dto.CartItemRequest;
import com.shop.model.CartItem;
import com.shop.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "Session-based shopping cart — no authentication required, identified solely by sessionId")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Resolves the effective session ID.
     * The X-Session-Id header is preferred over the URL path variable so that
     * callers can avoid embedding the session ID in a loggable URL.
     */
    private String resolveSessionId(String header, String pathVar) {
        return (header != null && !header.isBlank()) ? header : pathVar;
    }

    // GET /api/cart/{sessionId}  (or X-Session-Id header)
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get the cart for a session",
            description = "sessionId may be supplied via the X-Session-Id header (preferred) or the path variable.")
    @ApiResponse(responseCode = "200", description = "Cart items returned (empty list if none)")
    public List<CartItem> getCart(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader) {
        String sid = resolveSessionId(sessionIdHeader, sessionId);
        log.info("REST request to get cart for session: {}", sid);
        return cartService.getCart(sid);
    }

    // POST /api/cart/{sessionId}/add  (or X-Session-Id header)
    @PostMapping("/{sessionId}/add")
    @SuppressWarnings("null")
    @Operation(summary = "Add a product to the cart",
            description = "Increments quantity if the product is already in the cart. Validates against available stock.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item added or quantity incremented"),
        @ApiResponse(responseCode = "400", description = "Insufficient stock, or invalid quantity/productId"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public CartItem addToCart(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader,
            @Valid @RequestBody CartItemRequest request) {
        String sid = resolveSessionId(sessionIdHeader, sessionId);
        log.info("REST request to add product {} to cart for session: {}", request.getProductId(), sid);
        return cartService.addToCart(sid, request.getProductId(), request.getQuantity());
    }
    

    // DELETE /api/cart/item/{cartItemId}?sessionId=  (or X-Session-Id header)
    @DeleteMapping("/item/{cartItemId}")
    @Operation(summary = "Remove one item from the cart",
            description = "sessionId is required (via header or query param) and must match the item's owning session.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Item removed"),
        @ApiResponse(responseCode = "400", description = "sessionId not supplied"),
        @ApiResponse(responseCode = "403", description = "sessionId does not own this cart item"),
        @ApiResponse(responseCode = "404", description = "Cart item not found")
    })
    public ResponseEntity<Void> removeItem(
            @PathVariable @NonNull Long cartItemId,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader) {
        String sid = resolveSessionId(sessionIdHeader, sessionId);
        log.info("REST request to remove cart item : {} for session: {}", cartItemId, sid);
        cartService.removeFromCart(cartItemId, sid);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/cart/{sessionId}/clear  (or X-Session-Id header)
    @DeleteMapping("/{sessionId}/clear")
    @Operation(summary = "Empty the entire cart for a session")
    @ApiResponse(responseCode = "204", description = "Cart cleared")
    public ResponseEntity<Void> clearCart(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader) {
        String sid = resolveSessionId(sessionIdHeader, sessionId);
        log.info("REST request to clear cart for session: {}", sid);
        cartService.clearCart(sid);
        return ResponseEntity.noContent().build();
    }
}
