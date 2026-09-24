package com.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.dto.UpdateProfileRequest;
import com.ridelink.account.dto.UserResponse;
import com.ridelink.account.entity.Role;
import com.ridelink.account.exception.UserNotFoundException;
import com.ridelink.account.service.JwtService;
import com.ridelink.account.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired WebApplicationContext context;

    @MockitoBean UserService userService;
    @MockitoBean JwtService jwtService;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static final UserResponse RESPONSE = new UserResponse(
            1L, "John", "Doe", "john@example.com", Role.PASSENGER, true);

    @Test
    @WithMockUser(username = "john@example.com", roles = "PASSENGER")
    void getMe_authenticated_returns200() throws Exception {
        when(userService.getCurrentUser("john@example.com")).thenReturn(RESPONSE);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("PASSENGER"));
    }

    @Test
    void getMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "missing@example.com", roles = "PASSENGER")
    void getMe_userNotFound_returns404() throws Exception {
        when(userService.getCurrentUser("missing@example.com"))
                .thenThrow(new UserNotFoundException("missing@example.com"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "PASSENGER")
    void updateMe_validRequest_returns200() throws Exception {
        UserResponse updated = new UserResponse(
                1L, "Jane", "Smith", "john@example.com", Role.PASSENGER, true);

        when(userService.updateProfile(eq("john@example.com"), any())).thenReturn(updated);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");

        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "PASSENGER")
    void updateMe_blankFields_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMe_unauthenticated_returns401() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");

        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
