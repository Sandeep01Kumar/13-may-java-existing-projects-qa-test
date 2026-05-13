package com.jspider.spring_boot_simple_crud_with_mysql.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Rule Applied
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        System.out.println("[JwtUtilTest] Setting up JwtUtil with test configuration.");
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "ZmFrZS1zZWNyZXQta2V5LXJlcGxhY2UtaW4tcHJvZHVjdGlvbi0xMjM0NTY=");
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", 3600000L);
        try {
            ReflectionTestUtils.setField(jwtUtil, "jwtIssuer", "spring-boot-simple-crud");
        } catch (Exception ignored) {
            // Defensive: if the production class doesn't have jwtIssuer, ignore silently.
        }
        userDetails = new User("testuser", "ignored-password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void generateAndValidateToken_happyPath() {
        String token = jwtUtil.generateToken(userDetails);
        System.out.println("[JwtUtilTest] Generated token: " + token);

        assertNotNull(token, "Token must not be null");
        assertEquals("testuser", jwtUtil.extractUsername(token), "Subject claim must match the username");
        assertTrue(jwtUtil.isTokenValid(token, userDetails), "Freshly minted token must validate");
    }

    @Test
    void expiredToken_shouldBeInvalid() {
        // Force the token to be immediately expired by injecting a negative lifetime.
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", -1000L);
        String token = jwtUtil.generateToken(userDetails);
        System.out.println("[JwtUtilTest] Generated expired token for negative-lifetime test.");

        try {
            boolean valid = jwtUtil.isTokenValid(token, userDetails);
            assertFalse(valid, "Expired token must NOT validate");
        } catch (ExpiredJwtException expected) {
            // Acceptable — JJWT throws ExpiredJwtException during parse for expired tokens.
            System.out.println("[JwtUtilTest] Expired token threw ExpiredJwtException as alternative-acceptable outcome.");
        }
        System.out.println("[JwtUtilTest] Expired token rejection verified.");
    }

    @Test
    void tamperedToken_shouldFailParsing() {
        String token = jwtUtil.generateToken(userDetails);
        String tampered = token.substring(0, token.length() - 1) + "X";
        System.out.println("[JwtUtilTest] Tampering token; tampered=" + tampered);

        try {
            assertFalse(jwtUtil.isTokenValid(tampered, userDetails),
                    "Tampered token must NOT validate");
        } catch (JwtException expected) {
            // Acceptable — JJWT signature verification may throw on tampered tokens.
            System.out.println("[JwtUtilTest] Tampered token threw JwtException as alternative-acceptable outcome.");
        }
        System.out.println("[JwtUtilTest] Tampered token rejection verified.");
    }
}
