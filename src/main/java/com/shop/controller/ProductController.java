package com.shop.controller;

import com.shop.dto.ProductRequest;
import com.shop.model.Product;
import com.shop.service.ProductService;
import com.shop.exception.OrderException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Management of shop products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 500;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Get all products (paginated)", description = "Returns a page of products. Default page=0, size=20.")
    public Page<Product> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("REST request to get products page={} size={}", page, size);
        return productService.getAllProducts(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        log.info("REST request to get product : {}", id);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/category/{category}")
    public List<Product> getByCategory(@PathVariable String category) {
        return productService.getProductsByCategory(category);
    }

    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String name) {
        return productService.searchProducts(name);
    }

    @PostMapping
    @Operation(summary = "Create a new product", description = "Requires ADMIN role.")
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        log.info("REST request to create product : {}", request.getName());
        if (productService.existsByName(request.getName())) {
            log.warn("Product creation failed: Name '{}' already exists", request.getName());
            throw new OrderException("Product with name '" + request.getName() + "' already exists");
        }
        Product newProduct = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .category(request.getCategory())
                .build();
        return productService.createProduct(newProduct);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Requires ADMIN role.")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("REST request to delete product : {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
