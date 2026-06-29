package com.shop.apitests;

import io.restassured.http.Cookie;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class ProductApiTest extends ApiTestSupport {

    @Test
    void getAllProducts_ShouldReturnPagedContent() {
        given()
            .when().get("/api/products")
            .then().statusCode(200)
                .body("content", instanceOf(java.util.List.class))
                .body("content.size()", greaterThan(0));
    }

    @Test
    void getProductById_ShouldReturn404_WhenNotExists() {
        given()
            .when().get("/api/products/999999")
            .then().statusCode(404)
                .body("message", containsString("Product not found"));
    }

    @Test
    void getProductById_ShouldReturnProduct_WhenExists() {
        int firstId = given()
            .when().get("/api/products?size=1")
            .then().statusCode(200)
                .extract().path("content[0].id");

        given()
            .when().get("/api/products/" + firstId)
            .then().statusCode(200)
                .body("id", equalTo(firstId));
    }

    @Test
    void getByCategory_ShouldReturnEmptyList_WhenCategoryDoesNotExist() {
        given()
            .when().get("/api/products/category/NoSuchCategory12345")
            .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void searchProducts_ShouldReturnEmptyList_WhenNoMatch() {
        given()
            .when().get("/api/products/search?name=xyzzy_no_match_999")
            .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void createProduct_ShouldReturnForbidden_WithoutAdminJwt() {
        given()
            .contentType("application/json")
            .body("""
                {"name":"Unauthorized Product","description":"x","price":1.0,"stock":1,"category":"Test"}
                """)
            .when().post("/api/products")
            .then().statusCode(403);
    }

    @Test
    void createThenDeleteProduct_ShouldSucceed_AsAdmin() {
        Cookie admin = adminCookie();
        String name = "api-test-admin-product-" + uniqueSuffix();

        int id = given()
            .cookie(admin)
            .contentType("application/json")
            .body("""
                {"name":"%s","description":"created by api-tests","price":9.99,"stock":3,"category":"Test"}
                """.formatted(name))
            .when().post("/api/products")
            .then().statusCode(200)
                .body("name", equalTo(name))
                .extract().path("id");

        given()
            .cookie(admin)
            .when().delete("/api/products/" + id)
            .then().statusCode(204);

        given()
            .when().get("/api/products/" + id)
            .then().statusCode(404);
    }

    @Test
    void createProduct_ShouldReturnBadRequest_WhenNameAlreadyExists() {
        Cookie admin = adminCookie();
        String name = "api-test-dup-product-" + uniqueSuffix();

        given()
            .cookie(admin)
            .contentType("application/json")
            .body("""
                {"name":"%s","description":"first","price":5.0,"stock":1,"category":"Test"}
                """.formatted(name))
            .when().post("/api/products")
            .then().statusCode(200);

        given()
            .cookie(admin)
            .contentType("application/json")
            .body("""
                {"name":"%s","description":"second","price":6.0,"stock":1,"category":"Test"}
                """.formatted(name))
            .when().post("/api/products")
            .then().statusCode(400);
    }

    @Test
    void createProduct_ShouldReturnBadRequest_WhenPriceNotPositive() {
        Cookie admin = adminCookie();

        given()
            .cookie(admin)
            .contentType("application/json")
            .body("""
                {"name":"api-test-negative-price-%s","description":"x","price":-5.0,"stock":1,"category":"Test"}
                """.formatted(uniqueSuffix()))
            .when().post("/api/products")
            .then().statusCode(400)
                .body("message", equalTo("Price must be positive"));
    }

    @Test
    void createProduct_ShouldReturnBadRequest_WhenStockNegative() {
        Cookie admin = adminCookie();

        given()
            .cookie(admin)
            .contentType("application/json")
            .body("""
                {"name":"api-test-negative-stock-%s","description":"x","price":5.0,"stock":-1,"category":"Test"}
                """.formatted(uniqueSuffix()))
            .when().post("/api/products")
            .then().statusCode(400)
                .body("message", equalTo("Stock cannot be negative"));
    }

    @Test
    void deleteProduct_ShouldReturnForbidden_WithoutAdminJwt() {
        given()
            .when().delete("/api/products/" + firstProductId())
            .then().statusCode(403);
    }

    private int firstProductId() {
        return given()
            .when().get("/api/products?size=1")
            .then().statusCode(200)
                .extract().path("content[0].id");
    }
}
