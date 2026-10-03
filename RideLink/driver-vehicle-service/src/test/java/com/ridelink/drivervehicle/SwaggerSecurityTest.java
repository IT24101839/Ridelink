package com.ridelink.drivervehicle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "security.jwt-secret=01234567890123456789012345678901",
    "security.internal-token=swagger-test-token", "logging.level.root=WARN"})
@ActiveProfiles("local-integration")
class SwaggerSecurityTest {
    @Autowired TestRestTemplate http;

    @ParameterizedTest
    @ValueSource(strings={"/swagger-ui/index.html", "/swagger-ui/swagger-ui.css",
            "/swagger-ui/swagger-ui-bundle.js", "/swagger-ui/swagger-initializer.js",
            "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"})
    void documentationIsPublicAndExists(String path) {
        var response=http.getForEntity(path,String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotBlank();
        if(path.equals("/v3/api-docs"))assertThat(response.getBody()).contains("\"openapi\"");
    }
    @Test void swaggerShortcutIsPublic() {
        var response=http.getForEntity("/swagger-ui.html",String.class);
        assertThat(response.getStatusCode().value()).isIn(200,302);
        if(response.getStatusCode().value()==302)
            assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/swagger-ui/index.html");
    }
    @Test void normalApiStillRequiresJwt() {
        assertThat(http.getForEntity("/api/v1/drivers/missing",String.class).getStatusCode().value()).isEqualTo(401);
    }
    @Test void internalApiStillRequiresServiceToken() {
        assertThat(http.getForEntity("/api/internal/drivers/available",String.class).getStatusCode().value()).isEqualTo(401);
    }
    @Test void healthRemainsPublic() {
        assertThat(http.getForEntity("/api/driver-vehicle/health",String.class).getStatusCode().value()).isEqualTo(200);
    }
    @Test void openApiDefinesDistinctSchemesAndOperationRequirements() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var doc=mapper.readTree(http.getForObject("/v3/api-docs",String.class));
        var schemes=doc.path("components").path("securitySchemes");
        assertThat(schemes.path("bearerAuth").path("type").asText()).isEqualTo("http");
        assertThat(schemes.path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schemes.path("bearerAuth").path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(schemes.path("serviceToken").path("type").asText()).isEqualTo("apiKey");
        assertThat(schemes.path("serviceToken").path("in").asText()).isEqualTo("header");
        assertThat(schemes.path("serviceToken").path("name").asText()).isEqualTo("X-Service-Token");
        assertThat(doc.path("security").size()).isZero();
        assertSecurity(doc,"/api/v1/drivers","post","bearerAuth");
        assertSecurity(doc,"/api/v1/drivers/{id}","get","bearerAuth");
        assertSecurity(doc,"/api/v1/drivers/account/{accountId}","get","bearerAuth");
        for(String field:java.util.List.of("availability","location","service-areas"))
            assertSecurity(doc,"/api/v1/drivers/{id}/"+field,"patch","bearerAuth");
        assertSecurity(doc,"/api/v1/vehicles","post","bearerAuth");
        assertSecurity(doc,"/api/v1/vehicles/driver/{driverId}","get","bearerAuth");
        assertSecurity(doc,"/api/internal/drivers/available","get","serviceToken");
        assertSecurity(doc,"/api/internal/drivers/{id}","get","serviceToken");
        assertSecurity(doc,"/api/internal/drivers/account/{accountId}","get","serviceToken");
        assertSecurity(doc,"/api/internal/drivers/{id}/reservation","put","serviceToken");
        assertSecurity(doc,"/api/internal/drivers/{id}/reservation/{rideId}","delete","serviceToken");
        assertThat(doc.path("paths").path("/api/driver-vehicle/health").path("get").path("security").size()).isZero();
    }
    private void assertSecurity(com.fasterxml.jackson.databind.JsonNode doc,String path,String method,String scheme) {
        var security=doc.path("paths").path(path).path(method).path("security");
        assertThat(security.isArray()).as(path+" "+method).isTrue();
        assertThat(security.size()).isEqualTo(1);
        assertThat(security.get(0).size()).isEqualTo(1);
        assertThat(security.get(0).has(scheme)).isTrue();
        assertThat(security.get(0).path(scheme).isArray()).isTrue();
        assertThat(security.get(0).path(scheme).size()).isZero();
    }
}
