package com.digitalwallet.user.api;

import com.digitalwallet.testsupport.PostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AuthResourceIT {

    private String registeredEmail;

    @BeforeEach
    void registerUser() {
        registeredEmail = "alice+" + UUID.randomUUID() + "@example.com";
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "correct-password-12",
                        "base_currency": "USD"
                      }
                      """.formatted(registeredEmail))
                .when()
                .post("/users")
                .then()
                .statusCode(201);
    }

    @Test
    void postLogin_withValidCredentials_returns200_andBearerToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "correct-password-12"
                      }
                      """.formatted(registeredEmail))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("access_token", matchesPattern("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                .body("token_type", equalTo("Bearer"))
                .body("expires_in", equalTo(3600));
    }

    @Test
    void postLogin_withWrongPassword_returns401_authInvalidCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "wrong-password-xx"
                      }
                      """.formatted(registeredEmail))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("error_key", equalTo("auth.invalid_credentials"));
    }

    @Test
    void postLogin_withUnknownEmail_returns401_authInvalidCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "ghost@example.com",
                        "password": "correct-password-12"
                      }
                      """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("error_key", equalTo("auth.invalid_credentials"));
    }

    @Test
    void postLogin_unknownEmail_andWrongPassword_returnByteIdenticalEnvelope() {
        Response wrongPassword = given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "wrong-password-xx"
                      }
                      """.formatted(registeredEmail))
                .when()
                .post("/auth/login");
        Response unknownEmail = given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "ghost+%s@example.com",
                        "password": "correct-password-12"
                      }
                      """.formatted(UUID.randomUUID()))
                .when()
                .post("/auth/login");

        assertThat(wrongPassword.getStatusCode()).isEqualTo(unknownEmail.getStatusCode());
        assertThat(wrongPassword.jsonPath().getString("error_key"))
                .isEqualTo(unknownEmail.jsonPath().getString("error_key"));
        assertThat(wrongPassword.jsonPath().getString("message"))
                .isEqualTo(unknownEmail.jsonPath().getString("message"));
    }

    @Test
    void postLogin_withCaseInsensitiveEmail_returns200() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "correct-password-12"
                      }
                      """.formatted(registeredEmail.toUpperCase()))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue());
    }

    @Test
    void postLogin_withMissingPassword_returns400_validationInvalidPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s"
                      }
                      """.formatted(registeredEmail))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(400)
                .body("error_key", equalTo("validation.invalid_payload"));
    }
}
