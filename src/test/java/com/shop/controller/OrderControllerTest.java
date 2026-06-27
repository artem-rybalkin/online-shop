package com.shop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.dto.AuthRequest;
import com.shop.dto.CartItemRequest;
import com.shop.dto.OrderRequest;
import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.service.ProductService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductService productService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Product createProduct(String name, int stock) {
        return productService.createProduct(Product.builder()
                .name(name).description("Order test product")
                .price(100.0).stock(stock).category("Electronics").build());
    }

    @SuppressWarnings("null")
    private void addToCart(String sessionId, Long productId, int qty) throws Exception {
        CartItemRequest req = new CartItemRequest();
        req.setProductId(productId);
        req.setQuantity(qty);
        mockMvc.perform(post("/api/cart/" + sessionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    @SuppressWarnings("null")
    private Order placeOrder(String sessionId, String name, String email) throws Exception {
        OrderRequest req = OrderRequest.builder()
                .sessionId(sessionId).customerName(name).customerEmail(email).build();
        String resp = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(resp, Order.class);
    }

    // ── existing tests ────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void createOrder_ShouldReturnCreatedOrder_WhenValidRequest() throws Exception {
        Product product = createProduct("Order Create Product", 10);
        String sessionId = "order-session-create";
        addToCart(sessionId, product.getId(), 2);

        OrderRequest orderRequest = OrderRequest.builder()
                .sessionId(sessionId).customerName("John Doe").customerEmail("john@example.com").build();

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("John Doe"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @SuppressWarnings("null")
    @Test
    void getOrderById_ShouldReturnOrder_WhenOwnerRequests() throws Exception {
        Product product = createProduct("Order Get Product", 5);
        String sessionId = "order-session-get";
        addToCart(sessionId, product.getId(), 1);
        Order order = placeOrder(sessionId, "Jane Smith", "jane@example.com");

        mockMvc.perform(get("/api/orders/" + order.getId() + "?sessionId=" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.customerName").value("Jane Smith"));
    }

    @Test
    void getOrderById_ShouldReturnNotFound_WhenOrderDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/orders/99999?sessionId=test-session"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyOrders_ShouldReturnEmptyPage_WhenNoOrdersForSession() throws Exception {
        mockMvc.perform(get("/api/orders/my?sessionId=session-with-no-orders-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── business logic ────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void createOrder_ShouldReturnBadRequest_WhenCartIsEmpty() throws Exception {
        OrderRequest req = OrderRequest.builder()
                .sessionId("empty-cart-session-xyz")
                .customerName("No Items").customerEmail("empty@example.com").build();

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @SuppressWarnings("null")
    @Test
    void createOrder_ShouldReturnBadRequest_WhenStockIsInsufficient() throws Exception {
        Product product = createProduct("Low Stock Order Product", 1);
        String firstSession = "order-session-stockfail-first";
        addToCart(firstSession, product.getId(), 1);
        placeOrder(firstSession, "First Buyer", "first@example.com");

        String secondSession = "order-session-stockfail-second";
        addToCart(secondSession, product.getId(), 1);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(OrderRequest.builder()
                        .sessionId(secondSession)
                        .customerName("Second Buyer").customerEmail("second@example.com").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getOrderById_ShouldReturnForbidden_WhenWrongSessionId() throws Exception {
        Product product = createProduct("Order Ownership Product", 5);
        String ownerSession = "owner-order-session";
        addToCart(ownerSession, product.getId(), 1);
        Order order = placeOrder(ownerSession, "Owner", "owner@example.com");

        mockMvc.perform(get("/api/orders/" + order.getId() + "?sessionId=wrong-session-xyz"))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("null")
    @Test
    void createOrder_ShouldDecrementStock_AfterSuccessfulOrder() throws Exception {
        Product product = createProduct("Stock Decrement Product", 10);
        String sessionId = "order-session-stockcheck";
        addToCart(sessionId, product.getId(), 3);
        placeOrder(sessionId, "Stock Buyer", "stock@example.com");

        assertThat(productService.getProductById(product.getId()).getStock()).isEqualTo(7);
    }

    @SuppressWarnings("null")
    @Test
    void createOrder_ShouldClearCart_AfterSuccessfulOrder() throws Exception {
        Product product = createProduct("Cart Clear After Order Product", 10);
        String sessionId = "order-session-cartclear";
        addToCart(sessionId, product.getId(), 2);
        placeOrder(sessionId, "Cart Clear Buyer", "cartclear@example.com");

        mockMvc.perform(get("/api/cart/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings("null")
    @Test
    void getMyOrders_ShouldReturnOrders_ForGuestWithSessionId() throws Exception {
        Product product = createProduct("My Orders Test Product", 10);
        String sessionId = "my-orders-session-guest";
        addToCart(sessionId, product.getId(), 1);
        placeOrder(sessionId, "Guest User", "guest@example.com");

        mockMvc.perform(get("/api/orders/my?sessionId=" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sessionId").value(sessionId));
    }

    // ── Rec 4: previously missing ─────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void getMyOrders_ShouldReturnOrders_ForAuthenticatedUser() throws Exception {
        // Register and login a regular user
        AuthRequest creds = AuthRequest.builder()
                .username("orders-auth-user").password("password123")
                .email("orders-auth-user@test.com").build();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)));
        var loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)))
                .andReturn().getResponse();
        Cookie jwtCookie = loginResponse.getCookie("jwt");

        // Place an order while authenticated (send JWT so the order is linked to the user)
        Product product = createProduct("Auth User Order Product", 10);
        String sessionId = "auth-user-session";
        addToCart(sessionId, product.getId(), 1);
        mockMvc.perform(post("/api/orders")
                .cookie(jwtCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(OrderRequest.builder()
                        .sessionId(sessionId)
                        .customerName("Auth User").customerEmail("orders-auth-user@test.com").build())));

        // GET /my with JWT — should return orders tied to the authenticated user
        mockMvc.perform(get("/api/orders/my")
                .cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].customerEmail").value("orders-auth-user@test.com"));
    }

    // ── X-Session-Id header path ──────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void getMyOrders_ShouldReturnOrders_WhenXSessionIdHeaderProvided() throws Exception {
        Product product = createProduct("Header My Orders Product", 5);
        String sessionId = "header-my-orders-session";
        addToCart(sessionId, product.getId(), 1);
        placeOrder(sessionId, "Header User", "header.my@example.com");

        mockMvc.perform(get("/api/orders/my")
                .header("X-Session-Id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sessionId").value(sessionId));
    }

    @SuppressWarnings("null")
    @Test
    void getOrderById_ShouldReturnOrder_WhenXSessionIdHeaderProvided() throws Exception {
        Product product = createProduct("Header Order By Id Product", 5);
        String sessionId = "header-order-by-id-session";
        addToCart(sessionId, product.getId(), 1);
        Order order = placeOrder(sessionId, "Header User", "header.id@example.com");

        mockMvc.perform(get("/api/orders/" + order.getId())
                .header("X-Session-Id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    // ── BigDecimal total amount ────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void createOrder_ShouldCalculateTotalAmount_UsingBigDecimalArithmetic() throws Exception {
        // createProduct sets price=100.0; qty=3 → total must be exactly 300.00
        Product product = createProduct("Total Amount Verify Product", 10);
        String sessionId = "total-amount-verify-session";
        addToCart(sessionId, product.getId(), 3);
        Order order = placeOrder(sessionId, "Total Buyer", "total@example.com");

        assertThat(order.getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("300.00"));
    }

    // ── Rec 10: concurrent pessimistic-lock test ──────────────────────────────

    @Test
    @Disabled("H2 MVCC does not block concurrent SELECT FOR UPDATE between threads. " +
              "Run this test against a real MySQL instance to verify pessimistic locking behaviour.")
    void createOrder_ShouldAllowOnlyOneOrder_WhenTwoRequestsRaceForLastUnit() throws Exception {
        Product product = createProduct("Race Condition Product", 1);

        String session1 = "race-session-1";
        String session2 = "race-session-2";
        addToCart(session1, product.getId(), 1);
        addToCart(session2, product.getId(), 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (String session : List.of(session1, session2)) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    OrderRequest req = OrderRequest.builder()
                            .sessionId(session)
                            .customerName("Race Buyer").customerEmail("race@example.com").build();
                    int status = mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                            .andReturn().getResponse().getStatus();
                    statuses.add(status);
                } catch (Exception e) {
                    statuses.add(500);
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(statuses).hasSize(2);
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        assertThat(productService.getProductById(product.getId()).getStock()).isEqualTo(0);
    }
}
