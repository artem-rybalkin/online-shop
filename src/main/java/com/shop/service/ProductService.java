package com.shop.service;

import com.shop.exception.NotFoundException;
import com.shop.model.Product;
import com.shop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> getAllProducts(@NonNull Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Product getProductById(@NonNull Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + id));
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    @Transactional
    public Product createProduct(@NonNull Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(@NonNull Long id) {
        productRepository.deleteById(id);
    }

    public boolean existsByName(String name) {
        return productRepository.existsByName(name);
    }
}
