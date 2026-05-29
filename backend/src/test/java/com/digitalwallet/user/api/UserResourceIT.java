package com.digitalwallet.user.api;

import com.digitalwallet.testsupport.PostgresTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import io.restassured.http.ContentType;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserResourceIT {

    @Test
    @Order(1)
    void postUsers_withValidPayload_returns201_andUserIdPlusCreatedAt() {
        String email = "alice+" + UUID.randomUUID() + "@example.com";

        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "valid-password-12",
                        "base_currency": "USD"
                      }
                      """.formatted(email))
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .body("user_id", notNullValue())
                .body("user_id", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                .body("created_at", notNullValue());
    }

    @Test
    @Order(2)
    void postUsers_withDuplicateEmail_returns409_userEmailTaken() {
        String email = "duplicate+" + UUID.randomUUID() + "@example.com";
        String body = """
                {
                  "email": "%s",
                  "password": "valid-password-12",
                  "base_currency": "USD"
                }
                """.formatted(email);

        // First signup succeeds
        given().contentType(ContentType.JSON).body(body).post("/users").then().statusCode(201);

        // Second signup with the same email — even with different casing — must conflict.
        String upperBody = body.replace(email, email.toUpperCase());
        given()
                .contentType(ContentType.JSON)
                .body(upperBody)
                .when()
                .post("/users")
                .then()
                .statusCode(409)
                .body("error_key", equalTo("user.email_taken"));
    }

    @Test
    void postUsers_withInvalidBaseCurrency_returns400_validationInvalidPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "bob@example.com",
                        "password": "valid-password-12",
                        "base_currency": "XYZ"
                      }
                      """)
                .when()
                .post("/users")
                .then()
                .statusCode(400)
                .body("error_key", equalTo("validation.invalid_payload"));
    }

    @Test
    void postUsers_withLowerCaseBaseCurrency_returns400_validationInvalidPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "bob@example.com",
                        "password": "valid-password-12",
                        "base_currency": "usd"
                      }
                      """)
                .when()
                .post("/users")
                .then()
                .statusCode(400)
                .body("error_key", equalTo("validation.invalid_payload"));
    }

    @Test
    void postUsers_withShortPassword_returns400_validationInvalidPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "shorty@example.com",
                        "password": "short",
                        "base_currency": "USD"
                      }
                      """)
                .when()
                .post("/users")
                .then()
                .statusCode(400)
                .body("error_key", equalTo("validation.invalid_payload"));
    }

    @Test
    void postUsers_withMissingFields_returns400_validationInvalidPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "missing-fields@example.com"
                      }
                      """)
                .when()
                .post("/users")
                .then()
                .statusCode(400)
                .body("error_key", equalTo("validation.invalid_payload"));
    }

    @Test
    void postUsers_withMalformedEmail_returns400_validationInvalidPayload() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "not-an-email",
                        "password": "valid-password-12",
                        "base_currency": "USD"
                      }
                      """)
                .when()
                .post("/users")
                .then()
                .statusCode(400)
                .body("error_key", equalTo("validation.invalid_payload"));
    }

    @Test
    void postUsers_responseDoesNotLeakSensitiveFields() {
        String email = "leak+" + UUID.randomUUID() + "@example.com";

        given()
                .contentType(ContentType.JSON)
                .body("""
                      {
                        "email": "%s",
                        "password": "valid-password-12",
                        "base_currency": "EUR"
                      }
                      """.formatted(email))
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .body("password_hash", is(nullValueOrAbsent()))
                .body("email", is(nullValueOrAbsent()))
                .body("role", is(nullValueOrAbsent()));
    }

    private static org.hamcrest.Matcher<Object> nullValueOrAbsent() {
        return org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.nullValue(),
                org.hamcrest.Matchers.equalTo(null));
    }
}
