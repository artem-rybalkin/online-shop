package com.shop.apitests;

import io.restassured.RestAssured;
import io.restassured.http.Cookie;
import org.junit.jupiter.api.BeforeAll;

import static io.restassured.RestAssured.given;

/**
 * Base for black-box API tests: real HTTP calls against a running instance, no
 * Spring test context involved. Point it at any environment via -Dapi.baseUrl.
 */
public abstract class ApiTestSupport {

    @BeforeAll
    static void configureBaseUri() {
        RestAssured.baseURI = System.getProperty("api.baseUrl",
                System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080"));
    }

    static String uniqueSuffix() {
        return Long.toString(System.nanoTime());
    }

    /** Logs in as the seeded admin/admin user. Requires the app's default (non-"test") profile. */
    static Cookie adminCookie() {
        return given()
            .contentType("application/json")
            .body("""
                {"username":"admin","password":"admin"}
                """)
            .when().post("/api/auth/login")
            .then().statusCode(200)
                .extract().detailedCookie("jwt");
    }

    /**
     * Finds an existing product with at least minStock units available. Deliberately not
     * "just take the first product" — repeated runs against a real, persistent database
     * (not reset between runs, unlike the MockMvc/H2 suite) can and do deplete whichever
     * product happens to sort first.
     */
    @SuppressWarnings("unchecked")
    static int productWithStock(int minStock) {
        java.util.List<java.util.Map<String, Object>> content = given()
            .when().get("/api/products?size=100")
            .then().statusCode(200)
                .extract().path("content");

        return content.stream()
                .filter(p -> ((Number) p.get("stock")).intValue() >= minStock)
                .map(p -> ((Number) p.get("id")).intValue())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No product with at least " + minStock + " stock found — reseed the database"));
    }
}
