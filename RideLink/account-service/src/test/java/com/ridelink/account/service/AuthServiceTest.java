package com.ridelink.account.service;

import com.ridelink.account.dto.AuthResponse;
import com.ridelink.account.dto.LoginRequest;
import com.ridelink.account.dto.RegisterRequest;
import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.exception.AccountInactiveException;
import com.ridelink.account.exception.AdminRegistrationException;
import com.ridelink.account.exception.EmailAlreadyExistsException;
import com.ridelink.account.exception.InvalidCredentialsException;
import com.ridelink.account.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    @InjectMocks AuthService authService;

    private RegisterRequest registerRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setRole(Role.PASSENGER);

        savedUser = User.builder()
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
    void register_success_passenger() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = authService.register(registerRequest);

        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getRole()).isEqualTo(Role.PASSENGER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_success_driver() {
        registerRequest.setEmail("driver@example.com");
        registerRequest.setRole(Role.DRIVER);

        User driverUser = User.builder().id(2L).email("driver@example.com")
                .role(Role.DRIVER).active(true).firstName("Jane").lastName("Smith")
                .password("hashed").build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(driverUser);

        User result = authService.register(registerRequest);

        assertThat(result.getRole()).isEqualTo(Role.DRIVER);
    }

    @Test
    void register_adminRole_throwsAdminRegistrationException() {
        registerRequest.setRole(Role.ADMIN);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(AdminRegistrationException.class)
                .hasMessageContaining("ADMIN");

        verifyNoInteractions(userRepository);
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success() {
        LoginRequest req = loginRequest("john@example.com", "password123");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken("john@example.com", "PASSENGER", 1L))
                .thenReturn("mock.jwt.token");

        AuthResponse response = authService.login(req);

        assertThat(response.token()).isEqualTo("mock.jwt.token");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.role()).isEqualTo(Role.PASSENGER);
    }

    @Test
    void login_emailNotFound_throwsInvalidCredentialsException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("nobody@example.com", "pass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("john@example.com", "wrongpass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_inactiveAccount_throwsAccountInactiveException() {
        savedUser.setActive(false);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest("john@example.com", "password123")))
                .isInstanceOf(AccountInactiveException.class);
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }
}
