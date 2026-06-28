package com.shop.controller;

import com.shop.dto.OrderRequest;
import com.shop.model.Order;
import com.shop.service.OrderService;
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
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("REST request to create order for session: {}", request.getSessionId());
        return ResponseEntity.ok(orderService.createOrder(
                request.getSessionId(),
                request.getCustomerName(),
                request.getCustomerEmail()
        ));
    }

    @GetMapping("/my")
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
    public ResponseEntity<Order> getOrderById(
            @PathVariable @NonNull Long id,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionIdHeader) {
        String sid = hasText(sessionIdHeader) ? sessionIdHeader : sessionId;
        log.info("REST request to get order details for ID: {} (Session: {})", id, sid);
        return ResponseEntity.ok(orderService.getOrderById(id, sid));
    }
}