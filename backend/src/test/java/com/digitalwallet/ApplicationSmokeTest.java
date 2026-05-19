package com.digitalwallet;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ApplicationSmokeTest {

    @Test
    void liveness_endpoint_responds_ok() {
        int status = RestAssured.given().when().get("/q/health/live").statusCode();
        assertThat(status).isEqualTo(200);
    }
}
