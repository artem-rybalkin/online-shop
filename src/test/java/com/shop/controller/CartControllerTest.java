package com.shop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.dto.CartItemRequest;
import com.shop.model.CartItem;
import com.shop.model.Product;
import com.shop.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductService productService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Product createProduct(String name, int stock) {
        return productService.createProduct(Product.builder()
                .name(name)
                .description("Test product")
                .price(10.0)
                .stock(stock)
                .category("Test")
                .build());
    }

    @SuppressWarnings("null")
    private CartItem addToCart(String sessionId, Long productId, int qty) throws Exception {
        CartItemRequest req = new CartItemRequest();
        req.setProductId(productId);
        req.setQuantity(qty);
        String resp = mockMvc.perform(post("/api/cart/" + sessionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(resp, CartItem.class);
    }

    // ── existing tests (fixed) ────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void getCart_ShouldReturnEmptyList_WhenNoItems() throws Exception {
        mockMvc.perform(get("/api/cart/empty-session-xyz"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings("null")
    @Test
    void addToCart_ShouldReturnCartItem_WhenValidRequest() throws Exception {
        Product product = createProduct("Cart Add Test Product", 20);
        String sessionId = "test-session-add";

        CartItemRequest cartRequest = new CartItemRequest();
        cartRequest.setProductId(product.getId());
        cartRequest.setQuantity(2);

        mockMvc.perform(post("/api/cart/" + sessionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cartRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value(product.getId()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @SuppressWarnings("null")
    @Test
    void getCart_ShouldReturnCartItems_AfterAddingItems() throws Exception {
        Product product = createProduct("Cart Get Test Product", 15);
        String sessionId = "test-session-get";

        addToCart(sessionId, product.getId(), 3);

        mockMvc.perform(get("/api/cart/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].product.id").value(product.getId()))
                .andExpect(jsonPath("$[0].quantity").value(3));
    }

    @Test
    void removeItem_ShouldReturnNoContent_WhenOwnerRemovesItem() throws Exception {
        Product product = createProduct("Cart Remove Test Product", 10);
        String sessionId = "test-session-remove";

        CartItem cartItem = addToCart(sessionId, product.getId(), 1);

        mockMvc.perform(delete("/api/cart/item/" + cartItem.getId() + "?sessionId=" + sessionId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cart/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings("null")
    @Test
    void clearCart_ShouldReturnNoContent_AndEmptyCart() throws Exception {
        Product p1 = createProduct("Cart Clear Product 1", 5);
        Product p2 = createProduct("Cart Clear Product 2", 8);
        String sessionId = "test-session-clear";

        addToCart(sessionId, p1.getId(), 1);
        addToCart(sessionId, p2.getId(), 2);

        mockMvc.perform(delete("/api/cart/" + sessionId + "/clear"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cart/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── new tests ─────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void addToCart_ShouldIncrementQuantity_WhenSameProductAddedTwice() throws Exception {
        Product product = createProduct("Cart Increment Product", 20);
        String sessionId = "test-session-increment";

        addToCart(sessionId, product.getId(), 3);

        CartItemRequest second = new CartItemRequest();
        second.setProductId(product.getId());
        second.setQuantity(4);

        mockMvc.perform(post("/api/cart/" + sessionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7));
    }

    @SuppressWarnings("null")
    @Test
    void addToCart_ShouldReturnBadRequest_WhenIncrementalQuantityExceedsStock() throws Exception {
        Product product = createProduct("Cart Stock Check Product", 5);
        String sessionId = "test-session-stockcheck";

        addToCart(sessionId, product.getId(), 4);

        CartItemRequest overLimit = new CartItemRequest();
        overLimit.setProductId(product.getId());
        overLimit.setQuantity(3); // 4 already in cart + 3 > 5 stock

        mockMvc.perform(post("/api/cart/" + sessionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overLimit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @SuppressWarnings("null")
    @Test
    void addToCart_ShouldReturnNotFound_WhenProductDoesNotExist() throws Exception {
        CartItemRequest req = new CartItemRequest();
        req.setProductId(999999L);
        req.setQuantity(1);

        mockMvc.perform(post("/api/cart/test-session-noproduct/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_ShouldReturnForbidden_WhenSessionIdDoesNotMatchItem() throws Exception {
        Product product = createProduct("Cart Ownership Test Product", 10);
        String ownerSession = "owner-session-abc";
        String attackerSession = "attacker-session-xyz";

        CartItem cartItem = addToCart(ownerSession, product.getId(), 1);

        mockMvc.perform(delete("/api/cart/item/" + cartItem.getId() + "?sessionId=" + attackerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeItem_ShouldReturnNotFound_WhenCartItemDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/cart/item/999999?sessionId=any-session"))
                .andExpect(status().isNotFound());
    }

    @SuppressWarnings("null")
    @Test
    void removeItem_ShouldReturnBadRequest_WhenSessionIdMissing() throws Exception {
        Product product = createProduct("Cart No Session Product", 5);
        CartItem cartItem = addToCart("some-session", product.getId(), 1);

        mockMvc.perform(delete("/api/cart/item/" + cartItem.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sessionId is required"));
    }
}
