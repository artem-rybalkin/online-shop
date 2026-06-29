package com.shop.apitests;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class HealthApiTest extends ApiTestSupport {

    @Test
    void health_ShouldReturnUp() {
        given()
            .when().get("/actuator/health")
            .then().statusCode(200)
                .body("status", equalTo("UP"));
    }
}
