package com.ridelink.account.config;

import com.ridelink.account.entity.Role;
import com.ridelink.account.entity.User;
import com.ridelink.account.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local-integration")
public class LocalAdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public LocalAdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${LOCAL_ADMIN_EMAIL:}") String email,
            @Value("${LOCAL_ADMIN_PASSWORD:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "LOCAL_ADMIN_EMAIL and LOCAL_ADMIN_PASSWORD are required when the local-integration profile is active");
        }

        if (userRepository.existsByEmail(email)) {
            return;
        }

        userRepository.save(User.builder()
                .firstName("Local")
                .lastName("Admin")
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .active(true)
                .build());
    }
}