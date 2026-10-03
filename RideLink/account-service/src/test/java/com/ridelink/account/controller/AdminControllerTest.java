package com.ridelink.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.dto.RoleUpdateRequest;
import com.ridelink.account.dto.StatusUpdateRequest;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    WebApplicationContext context;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtService jwtService;

    MockMvc mockMvc;

    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static final UserResponse USER = new UserResponse(
            "user-1",
            "John",
            "Doe",
            "john@example.com",
            Role.PASSENGER,
            true
    );

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_adminToken_returns200() throws Exception {

        when(userService.getAllUsers())
                .thenReturn(List.of(USER));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email")
                        .value("john@example.com"));
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void getAllUsers_passengerToken_returns403() throws Exception {

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsers_unauthenticated_returns401() throws Exception {

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_deactivate_returns200() throws Exception {

        UserResponse deactivated = new UserResponse(
                "user-1",
                "John",
                "Doe",
                "john@example.com",
                Role.PASSENGER,
                false
        );

        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setActive(false);

        when(userService.updateStatus(eq("user-1"), any()))
                .thenReturn(deactivated);

        mockMvc.perform(
                        patch("/api/admin/users/user-1/status")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_userNotFound_returns404() throws Exception {

        when(userService.updateStatus(eq("missing-user"), any()))
                .thenThrow(
                        new UserNotFoundException(
                                "User not found with id: missing-user"
                        )
                );

        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setActive(false);

        mockMvc.perform(
                        patch("/api/admin/users/missing-user/status")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_missingBody_returns400() throws Exception {

        mockMvc.perform(
                        patch("/api/admin/users/user-1/status")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void updateStatus_passengerToken_returns403() throws Exception {

        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setActive(false);

        mockMvc.perform(
                        patch("/api/admin/users/user-1/status")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_returns200() throws Exception {

        UserResponse upgraded = new UserResponse(
                "user-1",
                "John",
                "Doe",
                "john@example.com",
                Role.DRIVER,
                true
        );

        RoleUpdateRequest req = new RoleUpdateRequest();
        req.setRole(Role.DRIVER);

        when(userService.updateRole(eq("user-1"), any()))
                .thenReturn(upgraded);

        mockMvc.perform(
                        patch("/api/admin/users/user-1/role")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_userNotFound_returns404() throws Exception {

        when(userService.updateRole(eq("missing-user"), any()))
                .thenThrow(
                        new UserNotFoundException(
                                "User not found with id: missing-user"
                        )
                );

        RoleUpdateRequest req = new RoleUpdateRequest();
        req.setRole(Role.DRIVER);

        mockMvc.perform(
                        patch("/api/admin/users/missing-user/role")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_missingBody_returns400() throws Exception {

        mockMvc.perform(
                        patch("/api/admin/users/user-1/role")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void updateRole_passengerToken_returns403() throws Exception {

        RoleUpdateRequest req = new RoleUpdateRequest();
        req.setRole(Role.DRIVER);

        mockMvc.perform(
                        patch("/api/admin/users/user-1/role")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isForbidden());
    }
}