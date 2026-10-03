package com.ridelink.ride.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.ride.exception.ErrorResponse;
import com.ridelink.ride.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            @Value("${ride.jwt-secret}") String secret,
            @Value("${ride.internal-token:}") String internalToken) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/ride/health", "/swagger-ui/**",
                                "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("SERVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(),
                                    new ErrorResponse(401, "Authentication required"));
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(),
                                    new ErrorResponse(403, "Access denied"));
                        }))
                .addFilterBefore(new JwtAuthenticationFilter(secret, internalToken, mapper),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
