package com.ridelink.drivervehicle.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.drivervehicle.exception.ErrorResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final SecretKey signingKey;
    private final String internalToken;
    private final ObjectMapper mapper;

    public JwtAuthenticationFilter(String secret, String internalToken, ObjectMapper mapper) {
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.internalToken = internalToken;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/internal/")) {
            String supplied = request.getHeader("X-Service-Token");
            if (internalToken.isBlank() || supplied == null || !MessageDigest.isEqual(
                    internalToken.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                unauthorized(response);
                return;
            }
            authenticate(new UserPrincipal("fare-service", "SERVICE"), "SERVICE");
        } else {
            String header = request.getHeader("Authorization");
            if (header != null) {
                if (!header.startsWith("Bearer ")) {
                    unauthorized(response);
                    return;
                }
                try {
                    Claims claims = Jwts.parser().verifyWith(signingKey).build()
                            .parseSignedClaims(header.substring(7)).getPayload();
                    String userId = claims.get("userId", String.class);
                    String role = claims.get("role", String.class);
                    if (role != null && role.startsWith("ROLE_")) {
                        role = role.substring(5);
                    }
                    if ("PASSENGER".equals(role)) {
                        role = "RIDER";
                    }
                    if (userId == null || userId.isBlank() || role == null
                            || !Set.of("RIDER", "DRIVER", "ADMIN").contains(role)
                            || claims.getExpiration() == null
                            || !claims.getExpiration().after(new Date())) {
                        unauthorized(response);
                        return;
                    }
                    authenticate(new UserPrincipal(userId, role), role);
                } catch (JwtException | IllegalArgumentException ex) {
                    unauthorized(response);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(UserPrincipal principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(401, "Authentication required"));
    }
}
