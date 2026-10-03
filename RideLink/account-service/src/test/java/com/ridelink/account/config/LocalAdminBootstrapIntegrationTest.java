package com.ridelink.account.config;

import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=01234567890123456789012345678901",
        "jwt.expiration-ms=600000",
        "LOCAL_ADMIN_EMAIL=admin@test.com",
        "LOCAL_ADMIN_PASSWORD=Admin123!",
        "logging.level.root=WARN"
})
@ActiveProfiles("local-integration")
class LocalAdminBootstrapIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    LocalAdminBootstrap bootstrap;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @Autowired
    ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        users.deleteAll();
        bootstrap.run();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void localProfileCreatesActiveAdminWithHashedPassword() {
        User admin = users.findByEmail("admin@test.com").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo("Admin123!");
        assertThat(passwordEncoder.matches("Admin123!", admin.getPassword())).isTrue();
    }

    @Test
    void repeatedBootstrapDoesNotCreateDuplicateAdmin() {
        bootstrap.run();
        bootstrap.run();

        assertThat(users.findAll().stream()
                .filter(user -> user.getEmail().equals("admin@test.com")))
                .hasSize(1);
    }

    @Test
    void adminCanLoginAndUseAdminEndpoint() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        User admin = users.findByEmail("admin@test.com").orElseThrow();

        assertThat(jwtService.extractAllClaims(token).get("role", String.class))
                .isEqualTo("ADMIN");
        assertThat(jwtService.extractAllClaims(token).getSubject())
                .isEqualTo("admin@test.com");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(admin.getEmail()));
    }

    @Test
    void publicAdminRegistrationRemainsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Public","lastName":"Admin","email":"public-admin@test.com",
                                 "password":"Admin123!","role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
