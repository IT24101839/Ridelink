package com.ridelink.account;

import com.ridelink.account.entity.*;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.service.JwtService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "jwt.secret=01234567890123456789012345678901","jwt.expiration-ms=600000",
    "LOCAL_ADMIN_EMAIL=admin@test.com","LOCAL_ADMIN_PASSWORD=Admin123!",
    "logging.level.root=WARN"})
@ActiveProfiles("local-integration")
class AccountLocalIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    @BeforeEach void setup(){
        users.deleteAll();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    String registration(String role){return """
        {"firstName":"Local","lastName":"Tester","email":"local@example.com",
         "password":"test-password","role":"%s"}
        """.formatted(role);}
    void register(String role) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration(role)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(role));
    }
    @ParameterizedTest @ValueSource(strings={"PASSENGER","DRIVER"})
    void registerLoginJwtAndAuthenticatedLookup(String role) throws Exception {
        register(role);
        var user=users.findByEmail("local@example.com").orElseThrow();
        assertThat(user.getRole().name()).isEqualTo(role);
        assertThat(user.getPassword()).isNotEqualTo("test-password");
        assertThat(passwords.matches("test-password",user.getPassword())).isTrue();
        String json=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"local@example.com","password":"test-password"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role))
                .andReturn().getResponse().getContentAsString();
        String token=mapper.readTree(json).get("token").asText();
        assertThat(jwt.isTokenValid(token)).isTrue();
        var claims=jwt.extractAllClaims(token);
        assertThat(claims.get("userId",String.class)).isEqualTo(user.getId());
        assertThat(claims.get("role",String.class)).isEqualTo(role);
        assertThat(claims.getSubject()).isEqualTo(user.getEmail());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value(role));
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+token))
                .andExpect(status().isForbidden());
        assertThat(users.findById(user.getId())).isPresent();
    }
    @Test void duplicateEmailRejectedByApiAndIndex() throws Exception {
        register("PASSENGER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration("DRIVER")))
                .andExpect(status().isConflict());
        var duplicate=User.builder().email("local@example.com").role(Role.DRIVER).build();
        assertThatThrownBy(()->users.insert(duplicate)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(users.count()).isEqualTo(1);
    }
    @Test void invalidPasswordAndAnonymousAccessRejected() throws Exception {
        register("PASSENGER");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"local@example.com\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }
    @Test void registrationValidationAndAdminRestrictionRemain() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration("ADMIN")))
                .andExpect(status().isBadRequest());
    }
    @Test void swaggerStillPublic() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
