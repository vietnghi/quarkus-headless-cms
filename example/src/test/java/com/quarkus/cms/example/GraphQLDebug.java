package com.quarkus.cms.example;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(GraphQLTestProfile.class)
class GraphQLDebug {
  @Test
  void debug() {
    // First register
    String testUser = "debug-user-" + System.currentTimeMillis();
    String registerResp = given()
        .redirects().follow(true)
        .contentType("application/json")
        .body(Map.of("username", testUser, "email", testUser + "@test.com", "password", "test123"))
        .when()
        .post("/admin/register")
        .then()
        .extract()
        .asString();
    System.out.println("REGISTER: " + registerResp);

    // Then login via GraphQL
    String loginQuery = "mutation { login(identifier: \"" + testUser + "\", password: \"test123\") { jwt user { id username email roles } } }";
    String loginResp = given()
        .contentType("application/json")
        .body(Map.of("query", loginQuery))
        .when()
        .post("/graphql")
        .then()
        .extract()
        .asString();
    System.out.println("LOGIN: " + loginResp);
  }
}
