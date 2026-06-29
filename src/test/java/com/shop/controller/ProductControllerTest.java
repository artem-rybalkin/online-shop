package com.shop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.dto.AuthRequest;
import com.shop.dto.ProductRequest;
import com.shop.model.Product;
import com.shop.model.User;
import com.shop.repository.UserRepository;
import com.shop.service.ProductService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductService productService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Cookie adminCookie;

    @BeforeEach
    void obtainAdminToken() throws Exception {
        // Create ADMIN user directly if not yet present
        if (userRepository.findByUsername("test-product-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("test-product-admin")
                    .email("test-product-admin@shop.test")
                    .password(passwordEncoder.encode("password123"))
                    .role("ADMIN")
                    .build());
        }

        AuthRequest creds = AuthRequest.builder()
                .username("test-product-admin")
                .password("password123")
                .email("test-product-admin@shop.test")
                .build();

        @SuppressWarnings("null")
        var loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)))
                .andReturn().getResponse();

        adminCookie = loginResponse.getCookie("jwt");
    }

    // ── GET (public) ──────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void getAllProducts_ShouldReturnPageOfProducts() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getProductById_ShouldReturnProduct_WhenExists() throws Exception {
        Product created = productService.createProduct(Product.builder()
                .name("GetById Test Product")
                .description("desc").price(100.0).stock(10).category("Test").build());

        mockMvc.perform(get("/api/products/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.name").value("GetById Test Product"));
    }

    @Test
    void getProductById_ShouldReturnNotFound_WhenNotExists() throws Exception {
        mockMvc.perform(get("/api/products/99999")).andExpect(status().isNotFound());
    }

    @SuppressWarnings("null")
    @Test
    void getByCategory_ShouldReturnFilteredProducts() throws Exception {
        productService.createProduct(Product.builder()
                .name("Category Test Laptop").description("desc")
                .price(1500.0).stock(5).category("TestElectronics").build());

        mockMvc.perform(get("/api/products/category/TestElectronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("TestElectronics"));
    }

    @Test
    void getByCategory_ShouldReturnEmptyList_WhenNoCategoryMatch() throws Exception {
        mockMvc.perform(get("/api/products/category/NonExistentCategory999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings("null")
    @Test
    void searchProducts_ShouldReturnMatchingProducts() throws Exception {
        productService.createProduct(Product.builder()
                .name("ZZ-Unique-Search-Token-Product").description("desc")
                .price(1200.0).stock(15).category("Electronics").build());

        mockMvc.perform(get("/api/products/search?name=ZZ-Unique-Search-Token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ZZ-Unique-Search-Token-Product"));
    }

    @Test
    void searchProducts_ShouldReturnEmptyList_WhenNoMatch() throws Exception {
        mockMvc.perform(get("/api/products/search?name=XYZZY_NO_MATCH_12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST / DELETE (require ADMIN) ─────────────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void createProduct_ShouldReturnCreatedProduct_WhenAdmin() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Auth-Created Product").description("Created with JWT")
                .price(200.0).stock(25).category("AuthTest").build();

        mockMvc.perform(post("/api/products")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Auth-Created Product"));
    }

    @SuppressWarnings("null")
    @Test
    void createProduct_ShouldReturnForbidden_WhenNoJwt() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Unauthorized Product Attempt").description("No")
                .price(50.0).stock(1).category("Test").build();

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("null")
    @Test
    void createProduct_ShouldReturnBadRequest_WhenNameAlreadyExists() throws Exception {
        productService.createProduct(Product.builder()
                .name("Duplicate Name Product").description("First")
                .price(10.0).stock(1).category("Test").build());

        ProductRequest duplicate = ProductRequest.builder()
                .name("Duplicate Name Product").description("Second")
                .price(20.0).stock(2).category("Test").build();

        mockMvc.perform(post("/api/products")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProduct_ShouldReturnNoContent_WhenAdmin() throws Exception {
        Product created = productService.createProduct(Product.builder()
                .name("Product To Delete Auth").description("Will be deleted")
                .price(50.0).stock(1).category("Test").build());

        mockMvc.perform(delete("/api/products/" + created.getId())
                .cookie(adminCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + created.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_ShouldReturnForbidden_WhenNoJwt() throws Exception {
        Product created = productService.createProduct(Product.builder()
                .name("Product Delete No JWT").description("No JWT")
                .price(10.0).stock(1).category("Test").build());

        mockMvc.perform(delete("/api/products/" + created.getId()))
                .andExpect(status().isForbidden());
    }

    // ── Rec 4: previously missing ──────────────────────────────────────────────

    @Test
    void deleteProduct_ShouldReturnNoContent_EvenWhenProductDoesNotExist() throws Exception {
        // Spring Data JPA 3's deleteById silently no-ops on a missing ID
        mockMvc.perform(delete("/api/products/999999")
                .cookie(adminCookie))
                .andExpect(status().isNoContent());
    }

    @SuppressWarnings("null")
    @Test
    void resetStock_ShouldSetStockOnEveryProduct_WhenAdmin() throws Exception {
        Product depleted = productService.createProduct(Product.builder()
                .name("Reset Stock Test Product").description("Will be reset")
                .price(10.0).stock(0).category("Test").build());

        mockMvc.perform(post("/api/products/reset-stock")
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(9999));

        mockMvc.perform(get("/api/products/" + depleted.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(9999));
    }

    @Test
    void resetStock_ShouldReturnForbidden_WhenNoJwt() throws Exception {
        mockMvc.perform(post("/api/products/reset-stock"))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("null")
    @Test
    void resetStock_ShouldUpdateEveryProduct_NotJustOne() throws Exception {
        Product first = productService.createProduct(Product.builder()
                .name("Reset Stock Multi Product 1").description("x")
                .price(10.0).stock(0).category("Test").build());
        Product second = productService.createProduct(Product.builder()
                .name("Reset Stock Multi Product 2").description("x")
                .price(10.0).stock(5).category("Test").build());

        long totalProductsBefore = productService.getAllProducts(org.springframework.data.domain.PageRequest.of(0, 1))
                .getTotalElements();

        mockMvc.perform(post("/api/products/reset-stock")
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value((int) totalProductsBefore));

        mockMvc.perform(get("/api/products/" + first.getId()))
                .andExpect(jsonPath("$.stock").value(9999));
        mockMvc.perform(get("/api/products/" + second.getId()))
                .andExpect(jsonPath("$.stock").value(9999));
    }

    @SuppressWarnings("null")
    @Test
    void resetStock_ShouldAcceptZero_AsAnExplicitStockValue() throws Exception {
        Product product = productService.createProduct(Product.builder()
                .name("Reset Stock Zero Product").description("x")
                .price(10.0).stock(50).category("Test").build());

        mockMvc.perform(post("/api/products/reset-stock?stock=0")
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0));

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(jsonPath("$.stock").value(0));
    }

    @SuppressWarnings("null")
    @Test
    void resetStock_ShouldAcceptALargeValue() throws Exception {
        Product product = productService.createProduct(Product.builder()
                .name("Reset Stock Large Value Product").description("x")
                .price(10.0).stock(1).category("Test").build());

        mockMvc.perform(post("/api/products/reset-stock?stock=" + Integer.MAX_VALUE)
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(Integer.MAX_VALUE));

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(jsonPath("$.stock").value(Integer.MAX_VALUE));
    }

    @Test
    void resetStock_ShouldReturnBadRequest_WhenStockIsNegative() throws Exception {
        mockMvc.perform(post("/api/products/reset-stock?stock=-1")
                .cookie(adminCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Stock cannot be negative"));
    }

    @SuppressWarnings("null")
    @Test
    void resetStock_ShouldNotAffectOtherFields_OnlyStock() throws Exception {
        Product product = productService.createProduct(Product.builder()
                .name("Reset Stock Field Isolation Product").description("Keep me")
                .price(42.5).stock(1).category("Test-Category").build());

        mockMvc.perform(post("/api/products/reset-stock")
                .cookie(adminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(jsonPath("$.name").value("Reset Stock Field Isolation Product"))
                .andExpect(jsonPath("$.description").value("Keep me"))
                .andExpect(jsonPath("$.price").value(42.5))
                .andExpect(jsonPath("$.category").value("Test-Category"))
                .andExpect(jsonPath("$.stock").value(9999));
    }
}
