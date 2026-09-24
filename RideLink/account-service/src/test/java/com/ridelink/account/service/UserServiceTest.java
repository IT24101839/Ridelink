package com.ridelink.account.service;

import com.ridelink.account.dto.RoleUpdateRequest;
import com.ridelink.account.dto.StatusUpdateRequest;
import com.ridelink.account.dto.UpdateProfileRequest;
import com.ridelink.account.dto.UserResponse;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.exception.UserNotFoundException;
import com.ridelink.account.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("hashed")
                .role(Role.PASSENGER)
                .active(true)
                .build();
    }

    @Test
    void getAllUsers_returnsMappedDtos() {
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("john@example.com");
        assertThat(result.get(0).role()).isEqualTo(Role.PASSENGER);
    }

    @Test
    void getCurrentUser_found_returnsDto() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        UserResponse result = userService.getCurrentUser("john@example.com");

        assertThat(result.email()).isEqualTo("john@example.com");
        assertThat(result.firstName()).isEqualTo("John");
    }

    @Test
    void getCurrentUser_notFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfile_success() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateProfile("john@example.com", req);

        assertThat(result.firstName()).isEqualTo("Jane");
        assertThat(result.lastName()).isEqualTo("Smith");
    }

    @Test
    void updateProfile_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");

        assertThatThrownBy(() -> userService.updateProfile("nobody@example.com", req))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateStatus_deactivate_success() {
        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateStatus(1L, req);

        assertThat(result.active()).isFalse();
    }

    @Test
    void updateStatus_activate_success() {
        user.setActive(false);
        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setActive(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateStatus(1L, req);

        assertThat(result.active()).isTrue();
    }

    @Test
    void updateStatus_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setActive(false);

        assertThatThrownBy(() -> userService.updateStatus(99L, req))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateRole_success() {
        RoleUpdateRequest req = new RoleUpdateRequest();
        req.setRole(Role.DRIVER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateRole(1L, req);

        assertThat(result.role()).isEqualTo(Role.DRIVER);
    }

    @Test
    void updateRole_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RoleUpdateRequest req = new RoleUpdateRequest();
        req.setRole(Role.DRIVER);

        assertThatThrownBy(() -> userService.updateRole(99L, req))
                .isInstanceOf(UserNotFoundException.class);
    }
}
