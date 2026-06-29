package com.shop.apitests.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.junitsupport.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;

import static io.restassured.RestAssured.given;

/**
 * Verifies the running API actually satisfies the contract recorded by the consumer
 * (contract-tests/consumer) — generated into contract-tests/pacts/. Regenerate the pact
 * file (cd contract-tests/consumer && npm run test:pact) whenever the consumer's expected
 * interactions change.
 */
@Provider("OnlineShopAPI")
@PactFolder("../contract-tests/pacts")
class ProviderPactVerificationTest {

    @BeforeEach
    void setTarget(PactVerificationContext context) throws Exception {
        String baseUrl = System.getProperty("api.baseUrl",
                System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080"));
        URI uri = new URI(baseUrl);
        context.setTarget(new HttpTestTarget(uri.getHost(), uri.getPort()));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("the admin user exists")
    void theAdminUserExists() {
        // Seeded by OnlineShopApplication's CommandLineRunner on app startup — nothing to do.
    }

    @State("a user does not exist with this username/password combination")
    void noSuchUser() {
    }

    @State("no user is authenticated")
    void noUserAuthenticated() {
    }

    @State("at least one product exists")
    void atLeastOneProductExists() {
    }

    @State("product 999999 does not exist")
    void productDoesNotExist() {
    }

    @State("product 1 exists with available stock")
    void product1HasStock() {
        // Make sure product 1 (seeded as "iPhone 14") has stock regardless of how much
        // earlier test runs against this same live database have consumed.
        String baseUrl = System.getProperty("api.baseUrl",
                System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080"));
        io.restassured.RestAssured.baseURI = baseUrl;
        io.restassured.http.Cookie admin = given()
            .contentType("application/json")
            .body("{\"username\":\"admin\",\"password\":\"admin\"}")
            .when().post("/api/auth/login")
            .then().statusCode(200)
                .extract().detailedCookie("jwt");
        given()
            .cookie(admin)
            .when().post("/api/products/reset-stock")
            .then().statusCode(200);
    }

    @State("order 1 exists and belongs to a different session")
    void order1BelongsToDifferentSession() {
        // Assumes the seed/test history of this dev database already has an order with id=1
        // (true for this repo's documented setup — see contract-tests/README.md). A from-scratch
        // database needs an order placed once before this state is satisfiable.
    }
}
