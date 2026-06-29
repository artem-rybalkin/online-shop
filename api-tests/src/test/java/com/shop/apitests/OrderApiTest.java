package com.shop.apitests;

import io.restassured.http.Cookie;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class OrderApiTest extends ApiTestSupport {

    @Test
    void placeOrder_ThenGetById_ShouldReturnTheOrder() {
        String sessionId = "api-test-order-" + uniqueSuffix();
        int productId = productWithStock(5);

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(200);

        int orderId = given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerName":"API Test Buyer","customerEmail":"apitest@example.com"}
                """.formatted(sessionId))
            .when().post("/api/orders")
            .then().statusCode(200)
                .body("customerName", equalTo("API Test Buyer"))
                .body("items", hasSize(1))
                .extract().path("id");

        given()
            .when().get("/api/orders/" + orderId + "?sessionId=" + sessionId)
            .then().statusCode(200)
                .body("id", equalTo(orderId));

        // Cart should be cleared after a successful order
        given()
            .when().get("/api/cart/" + sessionId)
            .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void createOrder_ShouldReturnBadRequest_WhenCartIsEmpty() {
        String sessionId = "api-test-empty-cart-" + uniqueSuffix();

        given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerName":"No Items","customerEmail":"empty@example.com"}
                """.formatted(sessionId))
            .when().post("/api/orders")
            .then().statusCode(400)
                .body("message", equalTo("Cart is empty"));
    }

    @Test
    void getOrderById_ShouldReturnForbidden_WhenWrongSessionId() {
        String sessionId = "api-test-order-ownership-" + uniqueSuffix();
        int productId = productWithStock(5);

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(200);

        int orderId = given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerName":"Owner","customerEmail":"owner@example.com"}
                """.formatted(sessionId))
            .when().post("/api/orders")
            .then().statusCode(200)
                .extract().path("id");

        given()
            .when().get("/api/orders/" + orderId + "?sessionId=wrong-session")
            .then().statusCode(403);
    }

    @Test
    void getOrderById_ShouldReturnNotFound_WhenOrderDoesNotExist() {
        given()
            .when().get("/api/orders/999999?sessionId=any-session")
            .then().statusCode(404);
    }

    @Test
    void createOrder_ShouldReturnBadRequest_WhenCustomerNameBlank() {
        String sessionId = "api-test-blank-name-" + uniqueSuffix();
        int productId = productWithStock(5);

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(200);

        // customerName omitted entirely (null) rather than "" — @Size also rejects ""
        // and Bean Validation doesn't guarantee which constraint's message wins when both fail.
        given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerEmail":"blank-name@example.com"}
                """.formatted(sessionId))
            .when().post("/api/orders")
            .then().statusCode(400)
                .body("message", equalTo("Customer name is required"));
    }

    @Test
    void createOrder_ShouldReturnBadRequest_WhenCustomerEmailInvalid() {
        String sessionId = "api-test-bad-email-" + uniqueSuffix();
        int productId = productWithStock(5);

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerName":"Bad Email Buyer","customerEmail":"not-an-email"}
                """.formatted(sessionId))
            .when().post("/api/orders")
            .then().statusCode(400)
                .body("message", equalTo("Must be a valid email address"));
    }

    @Test
    void createOrder_ShouldReturnBadRequest_WhenBodyIsMalformedJson() {
        given()
            .contentType("application/json")
            .body("{not valid json")
            .when().post("/api/orders")
            .then().statusCode(400)
                .body("message", equalTo("Malformed or missing request body"));
    }

    @Test
    void createOrder_ShouldReturnBadRequest_WhenStockDropsAfterAddToCart() {
        // Self-contained with a freshly admin-created product (own stock, isolated from
        // whatever shared seed-data stock other tests/suites in this run might be consuming).
        Cookie admin = adminCookie();
        String productName = "api-test-stock-recheck-" + uniqueSuffix();
        int productId = given()
            .cookie(admin)
            .contentType("application/json")
            .body("""
                {"name":"%s","description":"x","price":10.0,"stock":2,"category":"Test"}
                """.formatted(productName))
            .when().post("/api/products")
            .then().statusCode(200)
                .extract().path("id");

        String firstSession = "api-test-stock-recheck-first-" + uniqueSuffix();
        String secondSession = "api-test-stock-recheck-second-" + uniqueSuffix();

        // Both sessions add qty=2 while stock is still 2 — each addToCart check passes
        // independently since CartService only looks at its own session's cart.
        for (String sessionId : new String[] { firstSession, secondSession }) {
            given()
                .contentType("application/json")
                .body("""
                    {"productId":%d,"quantity":2}
                    """.formatted(productId))
                .when().post("/api/cart/" + sessionId + "/add")
                .then().statusCode(200);
        }

        // First order consumes all the stock.
        given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerName":"First Buyer","customerEmail":"first-%s@example.com"}
                """.formatted(firstSession, productId))
            .when().post("/api/orders")
            .then().statusCode(200);

        // Second order's own pessimistic-lock recheck must now reject it — stock is 0.
        given()
            .contentType("application/json")
            .body("""
                {"sessionId":"%s","customerName":"Second Buyer","customerEmail":"second-%s@example.com"}
                """.formatted(secondSession, productId))
            .when().post("/api/orders")
            .then().statusCode(400)
                .body("message", equalTo("Not enough stock for product: " + productName));
    }
}
