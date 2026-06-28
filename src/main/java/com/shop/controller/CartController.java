package com.shop.controller;

import com.shop.dto.CartItemRequest;
import com.shop.model.CartItem;
import com.shop.service.CartService;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cart")
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
    public ResponseEntity<Void> clearCart(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader) {
        String sid = resolveSessionId(sessionIdHeader, sessionId);
        log.info("REST request to clear cart for session: {}", sid);
        cartService.clearCart(sid);
        return ResponseEntity.noContent().build();
    }
}
