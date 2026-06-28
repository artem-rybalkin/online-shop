package com.shop.service;

import com.shop.model.*;
import com.shop.exception.OrderException;
import com.shop.exception.NotFoundException;
import com.shop.repository.OrderRepository;
import com.shop.repository.UserRepository;
import com.shop.repository.ProductRepository;
import com.shop.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, CartService cartService, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
    }

    @Transactional
    @SuppressWarnings("null")
    public Order createOrder(String sessionId, String customerName, String customerEmail) {
        log.info("Processing order placement for customer: {} (Session ID: {})", customerEmail, sessionId);
        List<CartItem> cartItems = cartService.getCart(sessionId);

        if (cartItems.isEmpty()) {
            log.warn("Order failed: Cart is empty for session {}", sessionId);
            throw new OrderException("Cart is empty");
        }

        // Перевірка та списання залишків (з pessimistic lock для уникнення race condition)
        for (CartItem item : cartItems) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new OrderException("Product no longer available: " + item.getProduct().getName()));
            if (product.getStock() < item.getQuantity()) {
                log.warn("Order failed: Insufficient stock for product '{}'. Requested: {}, Available: {}",
                    product.getName(), item.getQuantity(), product.getStock());
                throw new OrderException("Not enough stock for product: " + product.getName());
            }
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }

        // Конвертуємо CartItem → OrderItem
        List<OrderItem> orderItems = cartItems.stream().map(cartItem ->
            OrderItem.builder()
                .productName(cartItem.getProduct().getName())
                .price(BigDecimal.valueOf(cartItem.getProduct().getPrice()))
                .quantity(cartItem.getQuantity())
                .build()
        ).collect(Collectors.toList());

        // Рахуємо суму — BigDecimal arithmetic avoids floating-point rounding errors
        BigDecimal total = cartItems.stream()
            .map(i -> BigDecimal.valueOf(i.getProduct().getPrice())
                    .multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Отримуємо логін поточного користувача із контексту безпеки Spring Security
        String currentUsername = SecurityUtils.getCurrentUsername();
        User user = currentUsername != null ? userRepository.findByUsername(currentUsername).orElse(null) : null;

        Order order = Order.builder()
            .customerName(customerName)
            .customerEmail(customerEmail)
            .status("PENDING")
            .totalAmount(total)
            .sessionId(sessionId)
            .createdAt(LocalDateTime.now())
            .items(orderItems)
            .user(user)
            .build();

        orderItems.forEach(item -> item.setOrder(order));

        Order saved = orderRepository.save(order);

        log.info("Order placed by user: {}. Order ID: {}, Total Amount: ₴{}", customerEmail, saved.getId(), total);
        // Очищаємо кошик після замовлення
        cartService.clearCart(sessionId);

        return saved;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(@NonNull Long id, String sessionId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + id));

        // Security check: Ensure the order belongs to the requester
        String username = SecurityUtils.getCurrentUsername();

        boolean isOwner = false;
        if (username != null && order.getUser() != null) {
            isOwner = username.equals(order.getUser().getUsername());
        } else if (sessionId != null && sessionId.equals(order.getSessionId())) {
            isOwner = true;
        }

        if (!isOwner) {
            log.warn("Unauthorized access attempt to order ID: {} by user: {} / session: {}", id, username, sessionId);
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to view this order.");
        }

        return order;
    }

    public Page<Order> getOrdersForCurrentUser(String sessionId, Pageable pageable) {
        String username = SecurityUtils.getCurrentUsername();

        if (username != null && sessionId != null) {
            // Include guest orders placed under this sessionId before the user logged in.
            return orderRepository.findByUserUsernameOrSessionIdOrderByCreatedAtDesc(username, sessionId, pageable);
        } else if (username != null) {
            return orderRepository.findByUserUsernameOrderByCreatedAtDesc(username, pageable);
        } else if (sessionId != null) {
            return orderRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable);
        }
        return Page.empty(pageable);
    }
}