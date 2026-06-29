package com.shop.apitests;

import io.restassured.http.Cookie;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registerLoginMeLogout_ShouldFlowEndToEnd() {
        String username = "api-test-user-" + uniqueSuffix();
        String email = username + "@example.com";

        // Register
        Cookie registerCookie = given()
            .contentType("application/json")
            .body("""
                {"username":"%s","password":"password123","email":"%s"}
                """.formatted(username, email))
            .when().post("/api/auth/register")
            .then().statusCode(200)
                .body("username", equalTo(username))
                .extract().detailedCookie("jwt");
        org.junit.jupiter.api.Assertions.assertNotNull(registerCookie);

        // Login (no email field — matches what the real frontend sends)
        Cookie loginCookie = given()
            .contentType("application/json")
            .body("""
                {"username":"%s","password":"password123"}
                """.formatted(username))
            .when().post("/api/auth/login")
            .then().statusCode(200)
                .body("username", equalTo(username))
                .extract().detailedCookie("jwt");

        // /me without a cookie -> 401
        given()
            .when().get("/api/auth/me")
            .then().statusCode(401);

        // /me with the login cookie -> 200
        given()
            .cookie(loginCookie)
            .when().get("/api/auth/me")
            .then().statusCode(200)
                .body("username", equalTo(username));

        // Logout expires the cookie
        given()
            .cookie(loginCookie)
            .when().post("/api/auth/logout")
            .then().statusCode(200)
                .body("message", equalTo("Logged out"))
                .cookie("jwt", "");
    }

    @Test
    void login_ShouldReturnUnauthorized_ForWrongPassword() {
        given()
            .contentType("application/json")
            .body("""
                {"username":"nonexistent-api-test-user","password":"wrongpass"}
                """)
            .when().post("/api/auth/login")
            .then().statusCode(401)
                .body("message", equalTo("Invalid username or password"));
    }

    @Test
    void register_ShouldReturnBadRequest_WhenUsernameBlank() {
        given()
            .contentType("application/json")
            .body("""
                {"username":"","password":"password123","email":"blank@example.com"}
                """)
            .when().post("/api/auth/register")
            .then().statusCode(400)
                .body("message", equalTo("Username is required"));
    }

    @Test
    void register_ShouldReturnBadRequest_WhenPasswordBlank() {
        given()
            .contentType("application/json")
            .body("""
                {"username":"api-test-blank-pw-%s","password":"","email":"blank-pw@example.com"}
                """.formatted(uniqueSuffix()))
            .when().post("/api/auth/register")
            .then().statusCode(400)
                .body("message", equalTo("Password is required"));
    }

    @Test
    void register_ShouldReturnBadRequest_WhenEmailInvalid() {
        given()
            .contentType("application/json")
            .body("""
                {"username":"api-test-bad-email-%s","password":"password123","email":"not-an-email"}
                """.formatted(uniqueSuffix()))
            .when().post("/api/auth/register")
            .then().statusCode(400)
                .body("message", equalTo("Please provide a valid email address"));
    }

    @Test
    void register_ShouldReturnBadRequest_WhenUsernameAlreadyExists() {
        String username = "api-test-dup-" + uniqueSuffix();
        given()
            .contentType("application/json")
            .body("""
                {"username":"%s","password":"password123","email":"%s@example.com"}
                """.formatted(username, username))
            .when().post("/api/auth/register")
            .then().statusCode(200);

        given()
            .contentType("application/json")
            .body("""
                {"username":"%s","password":"password123","email":"%s-second@example.com"}
                """.formatted(username, username))
            .when().post("/api/auth/register")
            .then().statusCode(400)
                .body("error", equalTo("Username already exists"));
    }

    @Test
    void register_ShouldReturnBadRequest_WhenBodyIsMalformedJson() {
        given()
            .contentType("application/json")
            .body("{not valid json")
            .when().post("/api/auth/register")
            .then().statusCode(400)
                .body("message", equalTo("Malformed or missing request body"));
    }

    @Test
    void login_ShouldReturnMethodNotAllowed_ForGetRequest() {
        given()
            .when().get("/api/auth/login")
            .then().statusCode(405);
    }
}
