package se.laz.casual;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class CasualResourceTest
{
    @Test
    void testHelloEndpoint()
    {
        given()
          .when().get("/casual")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));
    }

}