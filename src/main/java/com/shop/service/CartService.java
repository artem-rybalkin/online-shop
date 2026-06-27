package com.shop.service;

import com.shop.model.CartItem;
import com.shop.model.Product;
import com.shop.exception.NotFoundException;
import com.shop.exception.OrderException;
import com.shop.repository.CartItemRepository;
import com.shop.repository.ProductRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository,
                       ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    public List<CartItem> getCart(String sessionId) {
        return cartItemRepository.findBySessionId(sessionId);
    }

    @Transactional
    @SuppressWarnings("null")
    public CartItem addToCart(String sessionId, @NonNull Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + productId));

        List<CartItem> existing = cartItemRepository.findBySessionId(sessionId);

        int alreadyInCart = existing.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .mapToInt(CartItem::getQuantity)
                .findFirst()
                .orElse(0);

        if (product.getStock() < alreadyInCart + quantity) {
            throw new OrderException("Insufficient stock for product: " + product.getName());
        }

        for (CartItem item : existing) {
            if (item.getProduct().getId().equals(productId)) {
                item.setQuantity(item.getQuantity() + quantity);
                return cartItemRepository.save(item);
            }
        }

        CartItem newItem = new CartItem(null, sessionId, product, quantity);
        return cartItemRepository.save(newItem);
    }

    public void removeFromCart(@NonNull Long cartItemId, String sessionId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new NotFoundException("Cart item not found with id: " + cartItemId));
        if (!item.getSessionId().equals(sessionId)) {
            throw new AccessDeniedException("You do not have permission to remove this cart item.");
        }
        cartItemRepository.deleteById(cartItemId);
    }

    @Transactional
    public void clearCart(String sessionId) {
        cartItemRepository.deleteBySessionId(sessionId);
    }
}
