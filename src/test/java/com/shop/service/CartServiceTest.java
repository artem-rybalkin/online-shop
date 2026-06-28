package com.shop.service;

import com.shop.model.CartItem;
import com.shop.model.Product;
import com.shop.repository.CartItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2's MVCC doesn't block concurrent SELECT FOR UPDATE between threads (see the disabled
 * race-condition test in OrderControllerTest), so the pessimistic lock in
 * CartService.addToCart can't be exercised deterministically here. Instead, these tests
 * verify the DB-level safety net: a unique constraint on (sessionId, product_id) that
 * rejects a duplicate cart row even if application-level locking is ever bypassed.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class CartServiceTest {

    @Autowired private CartService cartService;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private ProductService productService;

    private Product createProduct(String name, int stock) {
        return productService.createProduct(Product.builder()
                .name(name).description("Cart service test product")
                .price(10.0).stock(stock).category("Test").build());
    }

    @Test
    void addToCart_ShouldMergeIntoOneRow_WhenSameProductAddedTwice() {
        Product product = createProduct("Cart Service Merge Product", 20);
        String sessionId = "cart-service-merge-session";

        cartService.addToCart(sessionId, product.getId(), 2);
        cartService.addToCart(sessionId, product.getId(), 3);

        var items = cartItemRepository.findBySessionId(sessionId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void cartItems_ShouldRejectDuplicateRow_ForSameSessionAndProduct() {
        Product product = createProduct("Cart Unique Constraint Product", 10);
        String sessionId = "cart-unique-constraint-session";

        cartItemRepository.save(new CartItem(null, sessionId, product, 1));

        // A second row for the same (sessionId, product) pair must be rejected at the DB
        // level even if it somehow bypasses CartService's pessimistic-lock serialization.
        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(null, sessionId, product, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
