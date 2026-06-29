package com.shop.apitests;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class CartApiTest extends ApiTestSupport {

    @Test
    void addToCart_ThenGetCart_ShouldReturnTheItem() {
        String sessionId = "api-test-cart-" + uniqueSuffix();
        int productId = productWithStock(5);

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(200)
                .body("product.id", equalTo(productId))
                .body("quantity", equalTo(1));

        given()
            .when().get("/api/cart/" + sessionId)
            .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].product.id", equalTo(productId));
    }

    @Test
    void removeItem_ShouldReturnBadRequest_WhenSessionIdMissing() {
        String sessionId = "api-test-cart-no-session-" + uniqueSuffix();
        int productId = productWithStock(5);

        Integer cartItemId = given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(200)
                .extract().path("id");

        given()
            .when().delete("/api/cart/item/" + cartItemId)
            .then().statusCode(400)
                .body("message", equalTo("sessionId is required"));
    }

    @Test
    void addToCart_ShouldReturnNotFound_WhenProductDoesNotExist() {
        given()
            .contentType("application/json")
            .body("""
                {"productId":999999,"quantity":1}
                """)
            .when().post("/api/cart/api-test-cart-noproduct/add")
            .then().statusCode(404);
    }

    @Test
    void addToCart_ShouldReturnBadRequest_WhenQuantityIsZero() {
        int productId = productWithStock(0);

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":0}
                """.formatted(productId))
            .when().post("/api/cart/api-test-cart-zero-qty-" + uniqueSuffix() + "/add")
            .then().statusCode(400)
                .body("message", equalTo("Quantity must be at least 1"));
    }

    @Test
    void addToCart_ShouldReturnBadRequest_WhenProductIdMissing() {
        given()
            .contentType("application/json")
            .body("""
                {"quantity":1}
                """)
            .when().post("/api/cart/api-test-cart-no-product-id-" + uniqueSuffix() + "/add")
            .then().statusCode(400)
                .body("message", equalTo("Product ID is required"));
    }

    @Test
    void addToCart_ShouldReturnBadRequest_WhenStockInsufficient() {
        int productId = productWithStock(0);
        String sessionId = "api-test-cart-overstock-" + uniqueSuffix();

        int stock = given()
            .when().get("/api/products/" + productId)
            .then().statusCode(200)
                .extract().path("stock");

        given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":%d}
                """.formatted(productId, stock + 1))
            .when().post("/api/cart/" + sessionId + "/add")
            .then().statusCode(400)
                .body("message", containsString("Insufficient stock"));
    }

    @Test
    void removeItem_ShouldReturnNotFound_WhenCartItemDoesNotExist() {
        given()
            .when().delete("/api/cart/item/999999?sessionId=any-session")
            .then().statusCode(404);
    }

    @Test
    void removeItem_ShouldReturnForbidden_WhenSessionIdDoesNotMatchItem() {
        String ownerSession = "api-test-cart-owner-" + uniqueSuffix();
        int productId = productWithStock(5);

        int cartItemId = given()
            .contentType("application/json")
            .body("""
                {"productId":%d,"quantity":1}
                """.formatted(productId))
            .when().post("/api/cart/" + ownerSession + "/add")
            .then().statusCode(200)
                .extract().path("id");

        given()
            .when().delete("/api/cart/item/" + cartItemId + "?sessionId=someone-elses-session")
            .then().statusCode(403);
    }
}
