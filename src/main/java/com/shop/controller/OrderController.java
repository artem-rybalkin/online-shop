package com.shop.controller;

import com.shop.dto.OrderRequest;
import com.shop.model.Order;
import com.shop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order placement and retrieval for both guests (sessionId) and authenticated users (JWT)")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order from the current cart",
            description = "Atomically validates and decrements stock, snapshots cart items, then clears the cart.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order placed"),
        @ApiResponse(responseCode = "400", description = "Cart is empty, insufficient stock, or validation failed")
    })
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("REST request to create order for session: {}", request.getSessionId());
        return ResponseEntity.ok(orderService.createOrder(
                request.getSessionId(),
                request.getCustomerName(),
                request.getCustomerEmail()
        ));
    }

    @GetMapping("/my")
    @Operation(summary = "List the caller's orders",
            description = "Matches by JWT username and/or sessionId — a union, not either/or, so a guest order " +
                    "placed before login still shows up afterward if the same sessionId is still sent.")
    @ApiResponse(responseCode = "200", description = "Page of orders (possibly empty)")
    public ResponseEntity<Page<Order>> getMyOrders(
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String sid = hasText(sessionIdHeader) ? sessionIdHeader : sessionId;
        log.info("REST request to get orders for current session: {}", sid);
        return ResponseEntity.ok(orderService.getOrdersForCurrentUser(sid, PageRequest.of(page, Math.min(size, 50))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single order", description = "Ownership enforced by username OR sessionId match.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order returned"),
        @ApiResponse(responseCode = "403", description = "Caller does not own this order"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<Order> getOrderById(
            @PathVariable @NonNull Long id,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader) {
        String sid = hasText(sessionIdHeader) ? sessionIdHeader : sessionId;
        log.info("REST request to get order details for ID: {} (Session: {})", id, sid);
        return ResponseEntity.ok(orderService.getOrderById(id, sid));
    }
}