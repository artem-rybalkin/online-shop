package com.shop.controller;

import com.shop.dto.ProductRequest;
import com.shop.model.Product;
import com.shop.service.ProductService;
import com.shop.exception.OrderException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

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
    @ApiResponse(responseCode = "200", description = "Page of products")
    public Page<Product> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("REST request to get products page={} size={}", page, size);
        return productService.getAllProducts(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product returned"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<Product> getProductById(@PathVariable @NonNull Long id) {
        log.info("REST request to get product : {}", id);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Filter products by category")
    @ApiResponse(responseCode = "200", description = "Matching products (empty list if none)")
    public List<Product> getByCategory(@PathVariable String category) {
        return productService.getProductsByCategory(category);
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by name", description = "Case-insensitive substring match.")
    @ApiResponse(responseCode = "200", description = "Matching products (empty list if none)")
    public List<Product> searchProducts(@RequestParam String name) {
        return productService.searchProducts(name);
    }

    @PostMapping
    @Operation(summary = "Create a new product", description = "Requires ADMIN role.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product created"),
        @ApiResponse(responseCode = "400", description = "Name already exists, or validation failed"),
        @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    @SuppressWarnings("null")
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
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Product deleted (also returned if the id didn't exist)"),
        @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<Void> deleteProduct(@PathVariable @NonNull Long id) {
        log.info("REST request to delete product : {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-stock")
    @Operation(summary = "Reset stock for every product", description = "Requires ADMIN role. " +
            "Intended for test/dev environments to recover from stock depleted by repeated test runs.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock reset; body reports the new stock value and how many products were updated"),
        @ApiResponse(responseCode = "400", description = "stock was negative"),
        @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public ResponseEntity<Map<String, Object>> resetStock(@RequestParam(defaultValue = "9999") int stock) {
        if (stock < 0) {
            throw new OrderException("Stock cannot be negative");
        }
        log.info("REST request to reset stock for all products to {}", stock);
        int updatedCount = productService.resetAllStock(stock);
        return ResponseEntity.ok(Map.of("stock", stock, "updatedCount", updatedCount));
    }
}
