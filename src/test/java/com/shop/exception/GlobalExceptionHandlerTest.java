package com.shop.exception;

import com.shop.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that GlobalExceptionHandler never leaks raw DB error messages or
 * internal exception details into HTTP responses.
 *
 * Uses @MockitoBean on ProductService to force specific exception types; the full
 * Spring context is loaded so the real handler wiring is exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @BeforeEach
    void stubDefault() {
        // Prevent NPE for any getAllProducts() call not overridden per-test
        given(productService.getAllProducts(any())).willReturn(Page.empty());
    }

    @Test
    void handleDatabaseError_ShouldReturnGenericMessage_NotRawSqlDetails() throws Exception {
        given(productService.getAllProducts(any()))
                .willThrow(new org.springframework.dao.DataAccessResourceFailureException(
                        "Connection failed: FATAL: password authentication failed for user \"root\""));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("A database error occurred. Please try again later."))
                .andExpect(jsonPath("$.message").value(not(containsString("password"))))
                .andExpect(jsonPath("$.message").value(not(containsString("root"))));
    }

    @Test
    void handleAll_ShouldReturnGenericMessage_NotRawExceptionDetails() throws Exception {
        given(productService.getAllProducts(any()))
                .willThrow(new RuntimeException("NPE at ProductRepository line 42: internal secret"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.message").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.message").value(not(containsString("NPE"))));
    }

    @Test
    void handleNoResourceFound_ShouldReturn404_WithSafeMessage() throws Exception {
        // /api/products/** is permitAll in SecurityConfig, so Security passes it through.
        // No controller handles this nested path → NoResourceFoundException → 404.
        mockMvc.perform(get("/api/products/nonexistent/deep/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void handleDataIntegrityViolation_ShouldReturn409_WithConflictMessage() throws Exception {
        given(productService.getAllProducts(any()))
                .willThrow(new DataIntegrityViolationException("Duplicate entry 'iPhone 14' for key 'name'"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Data integrity error: possibly a duplicate name"))
                .andExpect(jsonPath("$.message").value(not(containsString("iPhone 14"))))
                .andExpect(jsonPath("$.message").value(not(containsString("Duplicate entry"))));
    }

    @Test
    void handleMethodNotSupported_ShouldReturn405_ForWrongHttpMethod() throws Exception {
        // GET on a POST-only endpoint → HttpRequestMethodNotSupportedException
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("HTTP method not supported: GET"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void handleUnreadableMessage_ShouldReturn400_ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{broken json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing request body"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void handleValidationErrors_ShouldReturn400_WhenRequiredFieldsMissing() throws Exception {
        // POST /api/orders with an empty body triggers @Valid on OrderRequest —
        // all @NotBlank fields fail → MethodArgumentNotValidException → 400
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
